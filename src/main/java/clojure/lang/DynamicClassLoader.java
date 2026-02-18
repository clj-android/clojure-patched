/**
 *   Copyright (c) Rich Hickey. All rights reserved.
 *   The use and distribution terms for this software are covered by the
 *   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *   which can be found in the file epl-v10.html at the root of this distribution.
 *   By using this software in any fashion, you are agreeing to be bound by
 * 	 the terms of this license.
 *   You must not remove this notice, or any other, from this software.
 **/

/* Android-patched version: extends ClassLoader instead of URLClassLoader.
 *
 * java.net.URLClassLoader does not exist on Android/ART. D8 silently drops
 * classes whose superclass is unresolvable, which cascaded to
 * AndroidDynamicClassLoader being dropped as well (since it extends this class).
 *
 * This patch preserves the full DynamicClassLoader API (classCache, defineClass,
 * findInMemoryClass, constants, etc.) while using ClassLoader as the base.
 * URL-based classpath management (addURL) is a no-op on Android since all code
 * is loaded from the APK's DEX files.
 */

package clojure.lang;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.Reference;
import java.net.URL;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class DynamicClassLoader extends ClassLoader {

    HashMap<Integer, Object[]> constantVals = new HashMap<Integer, Object[]>();

    static ConcurrentHashMap<String, Reference<Class>> classCache =
        new ConcurrentHashMap<String, Reference<Class>>();

    static final ReferenceQueue rq = new ReferenceQueue();

    public DynamicClassLoader() {
        // Match stock Clojure's logic: use context classloader if available
        // and not the system classloader, otherwise use Compiler's classloader.
        super(chooseParent());
    }

    public DynamicClassLoader(ClassLoader parent) {
        super(parent);
    }

    private static ClassLoader chooseParent() {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        if (context == null || context == ClassLoader.getSystemClassLoader()) {
            return Compiler.class.getClassLoader();
        }
        return context;
    }

    public Class defineClass(String name, byte[] bytes, Object srcForm) {
        Util.clearCache(rq, classCache);
        Class c = defineClass(name, bytes, 0, bytes.length);
        classCache.put(name, new SoftReference(c, rq));
        return c;
    }

    static Class<?> findInMemoryClass(String name) {
        Reference<Class> ref = classCache.get(name);
        if (ref != null) {
            Class<?> c = ref.get();
            if (c != null)
                return c;
            else
                classCache.remove(name, ref);
        }
        return null;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> c = findInMemoryClass(name);
        if (c != null)
            return c;
        return super.findClass(name);
    }

    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> c = findLoadedClass(name);
        if (c == null) {
            c = findInMemoryClass(name);
            if (c == null) {
                c = super.loadClass(name, false);
            }
        }
        if (resolve)
            resolveClass(c);
        return c;
    }

    public void registerConstants(int id, Object[] val) {
        constantVals.put(id, val);
    }

    public Object[] getConstants(int id) {
        return constantVals.get(id);
    }

    /**
     * No-op on Android. Stock Clojure uses this to add classpath entries via
     * URLClassLoader. On Android all code is loaded from APK DEX files.
     */
    public void addURL(URL url) {
        // no-op
    }
}
