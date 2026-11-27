package com.eclectics.worksync.projects.dto;

import java.util.UUID;
import java.util.regex.Pattern;
import tools.jackson.core.*;
import tools.jackson.databind.*;

/** Local to ownership transfer: accept UUID text only, never Jackson's Base64 UUID alternative. */
public class StrictProjectUuidDeserializer extends ValueDeserializer<UUID> {
    private static final Pattern UUID_TEXT=Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    @Override public UUID deserialize(JsonParser parser,DeserializationContext context) {
        if(!parser.hasToken(JsonToken.VALUE_STRING))return (UUID)context.handleUnexpectedToken(UUID.class,parser);
        String value=parser.getString();
        if(!UUID_TEXT.matcher(value).matches())return context.reportInputMismatch(UUID.class,"Invalid project UUID");
        return UUID.fromString(value);
    }
}
