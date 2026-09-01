package com.ftxeven.airauctions.core.command.tabcomplete;

import java.util.List;

public sealed interface TabPosition permits TabPosition.EntriesPosition, TabPosition.CopyPosition {

    record TabEntry(List<String> sources, String requires, List<String> conditions, boolean suffixMode) {
        public TabEntry {
            sources = List.copyOf(sources);
            conditions = List.copyOf(conditions);
        }
    }

    record EntriesPosition(List<TabEntry> entries) implements TabPosition {
        public EntriesPosition {
            entries = List.copyOf(entries);
        }
    }

    record CopyPosition(int copyArg, List<String> excludeSources, List<TabEntry> appendSources) implements TabPosition {
        public CopyPosition {
            excludeSources = List.copyOf(excludeSources);
            appendSources = List.copyOf(appendSources);
        }
    }
}