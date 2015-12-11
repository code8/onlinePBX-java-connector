package info.code8.pbx;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import info.code8.utils.URLEncoder;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Created by code8 on 12/11/15.
 */

public class OnlinePBXConnector implements OnlinePBXApi {
    public static final String API_URL = "http://api.onlinepbx.ru/";
    public static final String AUTH_URL = "/auth.json";

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiDomain;
    private final String apiKey;

    private AuthKey authKey;

    private static ThreadLocal<Mac> CRYPT = new ThreadLocal<Mac>() {
        @Override
        protected Mac initialValue() {
            try {
                return Mac.getInstance("HmacSHA1");
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }
    };

    public OnlinePBXConnector(ObjectMapper objectMapper, RestTemplate restTemplate) throws IOException {
        this.objectMapper = objectMapper == null ? createObjectMapper() : objectMapper;
        this.restTemplate = initRestTemplate(restTemplate == null ? new RestTemplate() : restTemplate);
        Properties authProps = new Properties();
        authProps.load(getClass().getClassLoader().getResourceAsStream("auth.properties"));
        apiDomain = authProps.getProperty("apiDomain");
        apiKey = authProps.getProperty("apiKey");
    }

    public AuthKey getAuthKey() {
        try {
            ObjectNode params = objectMapper.valueToTree(new Object());
            params.put("auth_key", apiKey);

            ResponseEntity<ObjectNode> response = invokeAPI(AUTH_URL, HttpMethod.POST, params);
            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode data = response.getBody().get("data");
                return new AuthKey(data.get("key_id").textValue(), data.get("key").textValue());
            }
        } catch (RestClientException e) {
            e.printStackTrace();
        }
        return null;
    }

    private ResponseEntity<ObjectNode> invokeAPI(String api, HttpMethod method, ObjectNode params) {

        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("Accept", "application/json");
        headers.add("Host", "api.onlinepbx.ru");
        headers.add("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8;");

        HttpEntity<ObjectNode> request;
        if (params == null) {
            request = new HttpEntity<>(headers);
        } else {
            request = new HttpEntity<>(params, headers);
        }

        try {

            if (authKey == null && !AUTH_URL.equals(api)) {
                authKey = getAuthKey();
            }

            ResponseEntity<ObjectNode> response =  restTemplate.exchange(API_URL + apiDomain + api, method, request, ObjectNode.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                if (response.getBody().get("status").asInt() == 0) {
                    if (response.getBody().get("comment").textValue().contains("auth")) {
                        // try to re-authenticate
                        authKey = getAuthKey();
                        response =  restTemplate.exchange(API_URL + apiDomain + api, method, request, ObjectNode.class);
                    }
                }

                if (response.getBody().get("status").asInt() == 0) {
                    return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
                }

            }
            return response;

        } catch (RestClientException e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS, true);
        objectMapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        objectMapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        return objectMapper;
    }

    private RestTemplate initRestTemplate(RestTemplate restTemplate) {
        restTemplate.getMessageConverters().add(new AbstractHttpMessageConverter<ObjectNode>() {
            @Override
            protected boolean canRead(MediaType mediaType) {
                return mediaType != null && Objects.equals(mediaType.getSubtype(), "json");
            }

            @Override
            protected boolean canWrite(MediaType mediaType) {
                return mediaType != null && (mediaType.getType() + "/" + mediaType.getSubtype()).equals(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
            }

            @Override
            protected boolean supports(Class<?> clazz) {
                return clazz.isAssignableFrom(ObjectNode.class);
            }

            @Override
            protected ObjectNode readInternal(Class<? extends ObjectNode> clazz, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
                return (ObjectNode) objectMapper.readTree(inputMessage.getBody());
            }

            @Override
            protected void writeInternal(ObjectNode jsonNodes, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {

                ClientHttpRequest request = (ClientHttpRequest) outputMessage;
                String data = StreamSupport.stream(Spliterators.spliteratorUnknownSize(jsonNodes.fields(), Spliterator.ORDERED), false)
                        .map(field -> field.getKey() + "=" + URLEncoder.encode(field.getValue().asText()))
                        .collect(Collectors.joining("&"));

                if (!isAuthRequest(request)) {
                    String dataMD5 = DigestUtils.md5Hex(data);
                    String method = request.getMethod().name();
                    MediaType mediaType = request.getHeaders().getContentType();
                    String contentType = mediaType.getType() + "/" + mediaType.getSubtype() + "; charset=UTF-8;";
                    String url = request.getURI().toString().replace("http://", "");
                    String hmacData = method+"\n"+dataMD5+"\n"+contentType+"\n"+url+"\n";

                    String hmac = makeHMAC(hmacData, authKey.getKeyId());
                    String signature = Base64.getEncoder().encodeToString(hmac.getBytes());

                    outputMessage.getHeaders().add("x-pbx-authentication", authKey.getKey() + ":" + signature);
                    outputMessage.getHeaders().add("Content-MD5", dataMD5);
                }


                OutputStream out = outputMessage.getBody();
                out.write(data.getBytes());
                out.flush();
            }

            private boolean isAuthRequest(ClientHttpRequest request) {
                return request.getURI().getPath().contains("auth");
            }

            private String makeHMAC(String data, String key) {
                try {
                    SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(), "HmacSHA1");

                    Mac mac = CRYPT.get();
                    mac.init(signingKey);

                    byte[] rawHmac = mac.doFinal(data.getBytes());

                    byte[] hexBytes = new Hex().encode(rawHmac);

                    return new String(hexBytes, "UTF-8");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });

        return restTemplate;
    }
}
