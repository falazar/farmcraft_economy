package com.falazar.farmupcraft.database;

import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class QueryBuilder<M, V> {
    private Predicate<Map.Entry<M, V>> predicate = entry -> true;

    public QueryBuilder<M, V> where(Predicate<V> condition) {
        predicate = predicate.and(entry -> condition.test(entry.getValue()));
        return this;
    }

    public QueryBuilder<M, V> and(Predicate<V> condition) {
        return where(condition);
    }

    public QueryBuilder<M, V> or(Predicate<V> condition) {
        Predicate<Map.Entry<M, V>> oldPredicate = predicate;
        predicate = entry -> oldPredicate.test(entry) || condition.test(entry.getValue());
        return this;
    }

    public Map<M, V> execute(DataBase<M, V> database) {
        return database.getDataMap().entrySet().stream()
                .filter(predicate)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}