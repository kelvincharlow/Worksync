package com.eclectics.worksync.projects.dto;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Applied only to CreateProjectRequest fields: JSON scalars must not be coerced into text. */
public class StrictProjectStringDeserializer extends ValueDeserializer<String> {
    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.hasToken(JsonToken.VALUE_STRING)) {
            return parser.getString();
        }
        return (String) context.handleUnexpectedToken(String.class, parser);
    }

    @Override
    public String getNullValue(DeserializationContext context) {
        return context.reportInputMismatch(String.class, "Expected a JSON string");
    }

    @Override
    public String getAbsentValue(DeserializationContext context) {
        // Optional record components may be omitted; @NotBlank still rejects an absent name.
        return null;
    }
}
