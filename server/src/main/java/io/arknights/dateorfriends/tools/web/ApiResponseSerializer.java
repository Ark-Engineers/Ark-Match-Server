package io.arknights.dateorfriends.tools.web;

import java.util.regex.Pattern;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

public class ApiResponseSerializer extends ValueSerializer<ApiResponse<?>> {
    private static final Pattern IP_FIELD = Pattern.compile("(?i:ip(?:s|_?address(?:es)?)?)|.*(?:Ip|IP|_ip)(?:s|S|Address|Addresses|_address|_addresses)?");

    @Override
    public void serialize(ApiResponse<?> response, JsonGenerator generator, SerializationContext context) {
        generator.writeStartObject();
        generator.writeNumberProperty("code", response.code());
        generator.writeStringProperty("message", IpUtils.maskInText(response.message()));
        generator.writeName("data");
        // 只修改序列化树的副本，保留业务对象及缓存中的原始 IP。
        JsonNode data = context.valueToTree(response.data());
        context.writeTree(generator, mask(data.deepCopy(), false));
        generator.writeEndObject();
    }

    private JsonNode mask(JsonNode node, boolean ipField) {
        if (node instanceof ObjectNode object) {
            boolean ipTarget = "IP".equalsIgnoreCase(object.path("targetType").asString(""));
            for (var entry : object.properties()) {
                var name = entry.getKey();
                object.set(name, mask(entry.getValue(), ipField || isIpField(name) || (ipTarget && "targetValue".equals(name))));
            }
        } else if (node instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) {
                array.set(i, mask(array.get(i), ipField));
            }
        } else if (node.isString()) {
            var value = node.asString();
            return StringNode.valueOf(ipField ? IpUtils.mask(value) : IpUtils.maskInText(value));
        }
        return node;
    }

    private boolean isIpField(String name) {
        return IP_FIELD.matcher(name).matches();
    }
}
