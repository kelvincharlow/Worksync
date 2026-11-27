package com.eclectics.worksync.projects.dto;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;
import tools.jackson.core.*;
import tools.jackson.databind.*;

/** PATCH dates use the contract's four-digit YYYY-MM-DD representation, or null. */
public class StrictProjectDateDeserializer extends ValueDeserializer<LocalDate> {
    private static final Pattern CALENDAR_DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");

    @Override public LocalDate deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) return (LocalDate) context.handleUnexpectedToken(LocalDate.class,parser);
        String value = parser.getString();
        if (!CALENDAR_DATE.matcher(value).matches()) {
            return context.reportInputMismatch(LocalDate.class,"Invalid project date");
        }
        try {
            LocalDate date = LocalDate.parse(value,DateTimeFormatter.ISO_LOCAL_DATE);
            if (date.getYear()<1) return context.reportInputMismatch(LocalDate.class,"Invalid project date");
            return date;
        }
        catch (DateTimeParseException invalid) { return context.reportInputMismatch(LocalDate.class,"Invalid project date"); }
    }
}
