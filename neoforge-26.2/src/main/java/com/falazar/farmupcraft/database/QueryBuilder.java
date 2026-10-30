package com.falazar.farmupcraft.database;

import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class QueryBuilder<M, V> {
    private Predicate<Map.Entry<M, V>> predicate = entry -> true;
    private boolean hasCondition;

    public QueryBuilder<M, V> where(Predicate<V> condition) {
        Objects.requireNonNull(condition, "condition");
        predicate = predicate.and(entry -> condition.test(entry.getValue()));
        hasCondition = true;
        return this;
    }

    public QueryBuilder<M, V> and(Predicate<V> condition) {
        return where(condition);
    }

    public QueryBuilder<M, V> or(Predicate<V> condition) {
        Objects.requireNonNull(condition, "condition");
        Predicate<Map.Entry<M, V>> next = entry -> condition.test(entry.getValue());
        predicate = hasCondition ? predicate.or(next) : next;
        hasCondition = true;
        return this;
    }

    public Map<M, V> execute(DataBase<M, V> database) {
        Predicate<Map.Entry<M, V>> current = predicate;
        return database.getDataMap().entrySet().stream().filter(current)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
