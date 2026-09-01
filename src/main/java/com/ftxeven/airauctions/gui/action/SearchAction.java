package com.ftxeven.airauctions.gui.action;

import com.ftxeven.airauctions.core.gui.action.DeferredNavigationAction;
import com.ftxeven.airauctions.gui.BaseGui;
import com.ftxeven.airauctions.gui.impl.SearchGui;
import com.ftxeven.airauctions.service.ServiceManager;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * [search] <optional gui:, page:, target:, filter:<dim>:<value>, sort:<dim>:<value>, restore:<...>, query:<answer>>
 */
public final class SearchAction extends DeferredNavigationAction {

    private static final String QUERY_KEY = "query";

    private final ServiceManager services;

    public SearchAction(ServiceManager services) {
        super("search");
        this.services = services;
    }

    @Override
    protected String defaultTarget() {
        return SearchGui.ID;
    }

    @Override
    protected @Nullable String directValueKey() {
        return QUERY_KEY;
    }

    @Override
    protected Map<String, Object> attributesFor(String answer) {
        String query = services.search().normalize(answer);
        return query != null ? Map.of(BaseGui.ATTR_SEARCH_QUERY, query) : Map.of();
    }
}