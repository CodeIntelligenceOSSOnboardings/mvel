package org.mvel;

import org.mvel.integration.Interceptor;
import org.mvel.compiler.AbstractParser;
import org.mvel.util.MethodStub;
import static org.mvel.util.ParseTools.getSimpleClassName;

import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashMap;
import static java.lang.Thread.currentThread;
import java.lang.reflect.Method;
import java.io.Serializable;

public class ParserConfiguration implements Serializable {
    protected Map<String, Object> imports;
    protected Set<String> packageImports;
    protected Map<String, Interceptor> interceptors;

    public ParserConfiguration() {
    }

    public ParserConfiguration(Map<String, Object> imports, Map<String, Interceptor> interceptors) {
        this.imports = imports;
        this.interceptors = interceptors;
    }


    public Set<String> getPackageImports() {
        return packageImports;
    }

    public void setPackageImports(Set<String> packageImports) {
        this.packageImports = packageImports;
    }


    public Class getImport(String name) {
        return (imports != null && imports.containsKey(name) ? (Class) imports.get(name) : (Class) AbstractParser.LITERALS.get(name));
    }

    public MethodStub getStaticImport(String name) {
        return imports != null ? (MethodStub) imports.get(name) : null;
    }

    public Object getStaticOrClassImport(String name) {
        return (imports != null && imports.containsKey(name) ? imports.get(name) : AbstractParser.LITERALS.get(name));
    }

    public void addPackageImport(String packageName) {
        if (packageImports == null) packageImports = new HashSet<String>();
        packageImports.add(packageName);
    }

    private boolean checkForDynamicImport(String className) {
        if (packageImports == null) return false;

        int found = 0;
        Class cls = null;
        for (String pkg : packageImports) {
            try {
                cls = currentThread().getContextClassLoader().loadClass(pkg + "." + className);
                found++;
            }
            catch (ClassNotFoundException e) {
                // do nothing.
            }
            catch (NoClassDefFoundError e) {
                if (e.getMessage().contains("wrong name")) {
                    // do nothing.  this is a weirdness in the jvm.
                    // see MVEL-43
                }
                else {
                    throw e;
                }
            }
        }

        if (found > 1) {
            throw new CompileException("ambiguous class name: " + className);
        }
        else if (found == 1) {
            addImport(className, cls);
            return true;
        }
        else {
            return false;
        }
    }

    public boolean hasImport(String name) {
        return (imports != null && imports.containsKey(name)) ||
                (!"this".equals(name) && !"self".equals(name) && !"empty".equals(name) && !"null".equals(name) &&
                        !"nil".equals(name) && !"true".equals(name) && !"false".equals(name)
                        && AbstractParser.LITERALS.containsKey(name))
                || checkForDynamicImport(name);
    }


    public void addImport(Class cls) {
        addImport(getSimpleClassName(cls), cls);
    }

    public void addImport(String name, Class cls) {
        if (this.imports == null) this.imports = new LinkedHashMap<String, Object>();
        this.imports.put(name, cls);
    }

    public void addImport(String name, Method method) {
        addImport(name, new MethodStub(method));
    }

    public void addImport(String name, MethodStub method) {
        if (this.imports == null) this.imports = new LinkedHashMap<String, Object>();
        this.imports.put(name, method);
    }

    public Map<String, Interceptor> getInterceptors() {
        return interceptors;
    }

    public void setInterceptors(Map<String, Interceptor> interceptors) {
        this.interceptors = interceptors;
    }

    public Map<String, Object> getImports() {
        return imports;
    }

    public void setImports(Map<String, Object> imports) {
        if (imports == null) return;

        Object val;
        for (String name : imports.keySet()) {
            if ((val = imports.get(name)) instanceof Class) {
                addImport(name, (Class) val);
            }
            else if (val instanceof Method) {
                addImport(name, (Method) val);
            }
            else if (val instanceof MethodStub) {
                addImport(name, (MethodStub) val);
            }
            else {
                throw new RuntimeException("invalid element in imports map: " + name + " (" + val + ")");
            }
        }
    }

    public boolean hasImports() {
        return (imports != null && imports.size() != 0) || (packageImports != null && packageImports.size() != 0);
    }
}
