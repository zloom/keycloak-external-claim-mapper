package org.zloom;

import com.google.auto.service.AutoService;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import org.apache.http.client.HttpResponseException;
import org.keycloak.broker.provider.util.SimpleHttp;
import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.ProtocolMapper;
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;
import org.keycloak.protocol.oidc.mappers.OIDCIDTokenMapper;
import org.keycloak.protocol.oidc.mappers.UserInfoTokenMapper;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.representations.IDToken;
import org.jboss.logging.Logger;
import org.keycloak.util.JsonSerialization;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;

@AutoService(ProtocolMapper.class)
public class ExternalClaimMapper extends AbstractOIDCProtocolMapper implements OIDCAccessTokenMapper, OIDCIDTokenMapper, UserInfoTokenMapper {
    private static final String USER_ID_PLACEHOLDER = "**uid**";
    private static final String USER_NAME_PLACEHOLDER = "**uname**";
    private static final Logger LOGGER = Logger.getLogger(ExternalClaimMapper.class);
    private static final String REMOTE_URL_PROPERTY = "remoteUrl";
    private static final String JSON_PATH_EXPRESSION_PROPERTY = "jsonPath";
    private static final String PROPAGATE_REMOTE_ERROR_PROPERTY = "propagateError";
    private static final String USER_AUTH_PROPERTY = "userAuth";
    private static final String REQUEST_HEADERS_PROPERTY = "requestHeaders";
    static final List<ProviderConfigProperty> PROPERTIES_CONFIG;
    static final Configuration JSON_PATH_CONFIG;

    static {
        var propertiesBuilder = ProviderConfigurationBuilder.create();

        propertiesBuilder
                .property()
                .name(REMOTE_URL_PROPERTY)
                .type(ProviderConfigProperty.STRING_TYPE)
                .label("Remote url")
                .helpText(String.format("Remote url to get claim for the given user, use %s and %s lowercase as placeholders", USER_ID_PLACEHOLDER, USER_NAME_PLACEHOLDER))
                .add();

        propertiesBuilder
                .property()
                .name(JSON_PATH_EXPRESSION_PROPERTY)
                .type(ProviderConfigProperty.STRING_TYPE)
                .label("Json path")
                .helpText("Json path expression, will be applied to response")
                .add();

        propertiesBuilder
                .property()
                .name(PROPAGATE_REMOTE_ERROR_PROPERTY)
                .type(ProviderConfigProperty.BOOLEAN_TYPE)
                .label("Error propagation")
                .defaultValue("false")
                .helpText("If enabled will throw errors, on remote endpoint error codes and on json transformation.")
                .add();

        propertiesBuilder
                .property()
                .name(USER_AUTH_PROPERTY)
                .type(ProviderConfigProperty.BOOLEAN_TYPE)
                .label("User authentication")
                .defaultValue("false")
                .helpText("Add current session user bearer token to remote endpoint request: Authorization: Bearer: ey... ")
                .add();

        propertiesBuilder
                .property()
                .name(REQUEST_HEADERS_PROPERTY)
                .type(ProviderConfigProperty.MAP_TYPE)
                .label("Request headers")
                .helpText(String.format("Configure headers attached to claim data request, use %s and %s lowercase as placeholders", USER_ID_PLACEHOLDER, USER_NAME_PLACEHOLDER))
                .add();

        PROPERTIES_CONFIG = propertiesBuilder.build();
        JSON_PATH_CONFIG = Configuration.builder()
                .mappingProvider(new JacksonMappingProvider(JsonSerialization.mapper))
                .jsonProvider(new JacksonJsonProvider(JsonSerialization.mapper))
                .build();

        OIDCAttributeMapperHelper.addAttributeConfig(PROPERTIES_CONFIG, ExternalClaimMapper.class);
    }

    private boolean IsEmpty(String value) {
        return value == null || value.isBlank();
    }

    @Override
    public String getId() {
        return "external-claim-mapper";
    }

    @Override
    public String getDisplayType() {
        return "External claim mapper";
    }

    @Override
    public String getHelpText() {
        return "Mapper that requests json endpoint and adds response as access token claim with configured name";
    }

    @Override
    public String getDisplayCategory() {
        return TOKEN_MAPPER_CATEGORY;
    }

    @Override
    public int getPriority() { return 100; }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return PROPERTIES_CONFIG;
    }

    public static boolean propagateError(ProtocolMapperModel model) {
        return "true".equals(model.getConfig().get(PROPAGATE_REMOTE_ERROR_PROPERTY));
    }

    public static boolean userAuth(ProtocolMapperModel model) {
        return "true".equals(model.getConfig().get(USER_AUTH_PROPERTY));
    }

    @Override
    protected void setClaim(IDToken token, ProtocolMapperModel model, UserSessionModel user, KeycloakSession session, ClientSessionContext clientSessionCtx) {
        var uid = user.getUser().getId();
        var uname = user.getUser().getUsername();
        var url = makeUrl(model, uid, uname);
        if (url == null) {
            return;
        }

        var claimData = getClaimData(model, token, url, uid, uname, session);
        if (IsEmpty(claimData)) {
            return;
        }

        var claimDataTransformed = transformResponse(model, claimData);
        if (claimDataTransformed == null) {
            return;
        }

        var claimName = model.getConfig().get(OIDCAttributeMapperHelper.TOKEN_CLAIM_NAME);
        try {
            LOGGER.infov("Setting user={0} claim={1}", uid, claimName);
            OIDCAttributeMapperHelper.mapClaim(token, model, claimDataTransformed);
        } catch (Exception e) {
            LOGGER.errorv(e, "Failed to set user={0} claim={1} data={2}", uid, claimName, claimDataTransformed);
        }
    }

    private String makeUrl(ProtocolMapperModel mappingModel, String uid, String uname) {
        var remoteUrl = mappingModel.getConfig().get(REMOTE_URL_PROPERTY);
        if (IsEmpty(remoteUrl)) {
            LOGGER.warn("Remote url is required");
            return null;
        }

        try {
            var remoteUrlWithPlaceholders = remoteUrl.replace(USER_ID_PLACEHOLDER, uid).replace(USER_NAME_PLACEHOLDER, uname);
            return new URL(remoteUrlWithPlaceholders).toString();
        } catch (MalformedURLException e) {
            LOGGER.errorv(e, "Could not create request url");
            return null;
        }
    }

    private String transformResponse(ProtocolMapperModel mapperModel, String data) {
        var jsonPath = mapperModel.getConfig().get(JSON_PATH_EXPRESSION_PROPERTY);
        if (IsEmpty(jsonPath)) {
            return data;
        }

        try {
            var dataObject = JsonPath.using(JSON_PATH_CONFIG).parse(data).read(jsonPath);
            return JsonSerialization.mapper.writeValueAsString(dataObject);
        } catch (Throwable e) {
            LOGGER.errorv(e, "Invalid jsonPath expression");
            if (propagateError(mapperModel)) {
                throw new RuntimeException(e);
            }
            return null;
        }
    }

    private SimpleHttp setAuth(ProtocolMapperModel model, SimpleHttp request, KeycloakSession session, IDToken token) {
        if (!userAuth(model)) {
            return request;
        }
        var encodedIdToken = session.tokens().encodeAndEncrypt(token);
        return request.auth(encodedIdToken);
    }

    private SimpleHttp setHeaders(ProtocolMapperModel model, SimpleHttp request, String uid, String uname) {
        var mapperModel = new IdentityProviderMapperModel();
        mapperModel.setConfig(model.getConfig());
        var headers = mapperModel.getConfigMap(REQUEST_HEADERS_PROPERTY);
        for (var header : headers.entrySet()) {
            var value = String.join(", ", header.getValue());
            var valueWithPlaceholders = value.replace(USER_ID_PLACEHOLDER, uid).replace(USER_NAME_PLACEHOLDER, uname);
            request.header(header.getKey(), valueWithPlaceholders);
        }

        return request;
    }

    private String getClaimData(ProtocolMapperModel model, IDToken token, String url, String uid, String uname, KeycloakSession session) {
        try {
            LOGGER.infov("Getting claim data for user={0} from url={1}", uid, url);
            var request = SimpleHttp.doGet(url, session);
            var response = setHeaders(model, setAuth(model, request, session, token), uid, uname).asResponse();
            var status = response.getStatus();
            var success = status >= 200 && status < 400;
            if (!success) {
                LOGGER.warnv("Claim data request failed status={1} response={2} ", uid, response.asString(), status);
                if (propagateError(model)) {
                    throw new HttpResponseException(status, response.asString());
                }
                return null;
            }

            var claimData = response.asString();
            LOGGER.infov("Got claim data for user={0} from url={1} length={2}", uid, url, claimData.length());
            LOGGER.debugv("Claim data for user={0} data={2}", uid, url, claimData);
            return claimData;
        } catch (Exception e) {
            LOGGER.errorv(e, "Could not get claim data for user={0} from url={1}", uid, url);
            if (propagateError(model)) {
                throw new RuntimeException(e);
            }
            return null;
        }
    }
}