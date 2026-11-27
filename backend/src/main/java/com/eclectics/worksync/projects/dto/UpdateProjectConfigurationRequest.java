package com.eclectics.worksync.projects.dto;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.*;
import tools.jackson.databind.JsonNode;
import com.eclectics.worksync.projects.entity.*;
/** Presence-aware arrays. Parse nodes locally to prevent Jackson scalar/enum coercion. */
public class UpdateProjectConfigurationRequest {
    private Set<ProjectOptionalView> views;
    private Set<ProjectWorkType> types;
    public void setEnabledViews(JsonNode node) { views=parse(node,ProjectOptionalView.class); }
    public void setEnabledWorkTypes(JsonNode node) { types=parse(node,ProjectWorkType.class); }
    private static <E extends Enum<E>> Set<E> parse(JsonNode node,Class<E> type) {
        if(node==null || !node.isArray())throw new IllegalArgumentException("Invalid feature collection");
        Set<E> result=EnumSet.noneOf(type);
        for(JsonNode value:node) {
            if(!value.isString() || !result.add(Enum.valueOf(type,value.asString())))
                throw new IllegalArgumentException("Invalid feature value");
        }
        return result;
    }
    @JsonAnySetter public void unknown(String key,Object value) { throw new IllegalArgumentException("Unsupported configuration field"); }
    public void validate() {
        if(views==null && types==null)throw new IllegalArgumentException("Empty configuration patch");
        if((views!=null&&!views.isEmpty()) || (types!=null&&!types.isEmpty()))throw new IllegalArgumentException("Unavailable feature");
    }
    public Set<ProjectOptionalView> views(){ return views; }
    public Set<ProjectWorkType> types(){ return types; }
}
