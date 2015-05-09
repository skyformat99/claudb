package tonivade.db.data;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Database implements IDatabase {

    private Map<String, DatabaseValue> cache = Collections.synchronizedMap(new HashMap<>());

    /**
     * @return
     * @see java.util.Map#size()
     */
    @Override
    public int size() {
        return cache.size();
    }

    /**
     * @return
     * @see java.util.Map#isEmpty()
     */
    @Override
    public boolean isEmpty() {
        return cache.isEmpty();
    }

    /**
     * @param key
     * @return
     * @see java.util.Map#containsKey(java.lang.Object)
     */
    @Override
    public boolean containsKey(Object key) {
        return cache.containsKey(key);
    }

    /**
     * @param value
     * @return
     * @see java.util.Map#containsValue(java.lang.Object)
     */
    @Override
    public boolean containsValue(Object value) {
        return cache.containsValue(value);
    }

    /**
     * @param key
     * @return
     * @see java.util.Map#get(java.lang.Object)
     */
    @Override
    public DatabaseValue get(Object key) {
        return cache.get(key);
    }

    /**
     * @param key
     * @param value
     * @return
     * @see java.util.Map#put(java.lang.Object, java.lang.Object)
     */
    @Override
    public DatabaseValue put(String key, DatabaseValue value) {
        return cache.put(key, value);
    }

    /**
     * @param key
     * @return
     * @see java.util.Map#remove(java.lang.Object)
     */
    @Override
    public DatabaseValue remove(Object key) {
        return cache.remove(key);
    }

    /**
     * @param m
     * @see java.util.Map#putAll(java.util.Map)
     */
    @Override
    public void putAll(Map<? extends String, ? extends DatabaseValue> m) {
        cache.putAll(m);
    }

    /**
     *
     * @see java.util.Map#clear()
     */
    @Override
    public void clear() {
        cache.clear();
    }

    /**
     * @return
     * @see java.util.Map#keySet()
     */
    @Override
    public Set<String> keySet() {
        return cache.keySet();
    }

    /**
     * @return
     * @see java.util.Map#values()
     */
    @Override
    public Collection<DatabaseValue> values() {
        return cache.values();
    }

    /**
     * @return
     * @see java.util.Map#entrySet()
     */
    @Override
    public Set<java.util.Map.Entry<String, DatabaseValue>> entrySet() {
        return cache.entrySet();
    }

    @Override
    public DatabaseValue putIfAbsent(String key, DatabaseValue value) {
        return cache.putIfAbsent(key, value);
    }

}
