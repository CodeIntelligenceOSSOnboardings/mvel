package org.mvel2.ast;

import org.mvel2.CompileException;
import static org.mvel2.DataConversion.canConvert;
import static org.mvel2.DataConversion.convert;
import org.mvel2.UnresolveablePropertyException;
import org.mvel2.compiler.ExecutableStatement;
import org.mvel2.integration.VariableResolver;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.integration.impl.MapVariableResolver;
import org.mvel2.integration.impl.MapVariableResolverFactory;
import org.mvel2.util.CallableProxy;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Proto extends ASTNode {
    private String name;
    private Map<String, Receiver> receivers;

    public Proto(String name) {
        this.name = name;
        this.receivers = new HashMap<String, Receiver>();
    }

    public void declareReceiver(String name, Function function) {
        receivers.put(name, new Receiver(null, ReceiverType.FUNCTION, function));
    }

    public void declareReceiver(String name, Class type, ExecutableStatement initCode) {
        receivers.put(name, new Receiver(null, ReceiverType.PROPERTY, initCode));
    }

    public ProtoInstance newInstance(Object ctx, Object thisCtx, VariableResolverFactory factory) {
        return new ProtoInstance(this, ctx, thisCtx, factory);
    }

    @Override
    public Object getReducedValue(Object ctx, Object thisValue, VariableResolverFactory factory) {
        factory.createVariable(name, this);
        return this;
    }

    @Override
    public Object getReducedValueAccelerated(Object ctx, Object thisValue, VariableResolverFactory factory) {
        factory.createVariable(name, this);
        return this;
    }

    public class Receiver implements CallableProxy {
        private ReceiverType type;
        private Object receiver;
        private ExecutableStatement initValue;
        private ProtoInstance instance;

        public Receiver(ProtoInstance protoInstance, ReceiverType type, Object receiver) {
            this.instance = protoInstance;
            this.type = type;
            this.receiver = receiver;
        }

        public Receiver(ProtoInstance protoInstance, ReceiverType type, ExecutableStatement stmt) {
            this.instance = protoInstance;
            this.type = type;
            this.initValue = stmt;
        }

        public Object call(Object ctx, Object thisCtx, VariableResolverFactory factory, Object[] parms) {
            switch (type) {
                case FUNCTION:
                    return ((Function) receiver).call(ctx, thisCtx, new InvokationContextFactory(factory, instance.instanceStates), parms);
                case PROPERTY:
                    return receiver;
            }
            return null;
        }

        public void setInitValue(ExecutableStatement stmt) {
            initValue = stmt;
        }

        public Receiver init(ProtoInstance instance, Object ctx, Object thisCtx, VariableResolverFactory factory) {
            return new Receiver(instance, type,
                    type == ReceiverType.PROPERTY && initValue != null ? initValue.getValue(ctx, thisCtx, factory) :
                            receiver);
        }
    }

    public enum ReceiverType {
        FUNCTION, MAPPED_METHOD, PROPERTY
    }

    public class ProtoInstance implements Map<String, Receiver> {
        private Proto protoType;
        private VariableResolverFactory instanceStates;
        private Map<String, Receiver> receivers;

        public ProtoInstance(Proto protoType, Object ctx, Object thisCtx, VariableResolverFactory factory) {
            this.protoType = protoType;

            receivers = new HashMap<String, Receiver>();
            for (Map.Entry<String, Receiver> entry : protoType.receivers.entrySet()) {
                receivers.put(entry.getKey(), entry.getValue().init(this, ctx, thisCtx, factory));
            }

            instanceStates = new ProtoContextFactory(receivers);
        }

        public int size() {
            return receivers.size();
        }

        public boolean isEmpty() {
            return receivers.isEmpty();
        }

        public boolean containsKey(Object key) {
            return receivers.containsKey(key);
        }

        public boolean containsValue(Object value) {
            return receivers.containsValue(value);
        }

        public Receiver get(Object key) {
            return receivers.get(key);
        }

        public Receiver put(String key, Receiver value) {
            return receivers.put(key, value);
        }

        public Receiver remove(Object key) {
            return receivers.remove(key);
        }

        public void putAll(Map m) {
        }

        public void clear() {
        }

        public Set<String> keySet() {
            return receivers.keySet();
        }

        public Collection<Receiver> values() {
            return receivers.values();
        }

        public Set<Entry<String, Receiver>> entrySet() {
            return receivers.entrySet();
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "proto " + name;
    }

    public class ProtoContextFactory extends MapVariableResolverFactory {
        public ProtoContextFactory(Map variables) {
            super(variables);
        }

        @Override
        public VariableResolver createVariable(String name, Object value) {
            VariableResolver vr;

            try {
                (vr = getVariableResolver(name)).setValue(value);
                return vr;
            }
            catch (UnresolveablePropertyException e) {
                addResolver(name, vr = new ProtoResolver(variables, name)).setValue(value);
                return vr;
            }
        }

        @Override
        public VariableResolver createVariable(String name, Object value, Class<?> type) {
            VariableResolver vr;
            try {
                vr = getVariableResolver(name);
            }
            catch (UnresolveablePropertyException e) {
                vr = null;
            }

            if (vr != null && vr.getType() != null) {
                throw new CompileException("variable already defined within scope: " + vr.getType() + " " + name);
            }
            else {
                addResolver(name, vr = new ProtoResolver(variables, name, type)).setValue(value);
                return vr;
            }
        }

        public VariableResolver getVariableResolver(String name) {
            VariableResolver vr = variableResolvers.get(name);
            if (vr != null) {
                return vr;
            }
            else if (variables.containsKey(name)) {
                variableResolvers.put(name, vr = new ProtoResolver(variables, name));
                return vr;
            }
            else if (nextFactory != null) {
                return nextFactory.getVariableResolver(name);
            }

            throw new UnresolveablePropertyException("unable to resolve variable '" + name + "'");
        }
    }

    public class ProtoResolver implements VariableResolver {
        private String name;
        private Class<?> knownType;
        private Map<String, Object> variableMap;

        public ProtoResolver(Map<String, Object> variableMap, String name) {
            this.variableMap = variableMap;
            this.name = name;
        }

        public ProtoResolver(Map variableMap, String name, Class knownType) {
            this.name = name;
            this.knownType = knownType;
            this.variableMap = variableMap;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setStaticType(Class knownType) {
            this.knownType = knownType;
        }

        public String getName() {
            return name;
        }

        public Class getType() {
            return knownType;
        }

        public void setValue(Object value) {
            if (knownType != null && value != null && value.getClass() != knownType) {
                if (!canConvert(knownType, value.getClass())) {
                    throw new CompileException("cannot assign " + value.getClass().getName() + " to type: "
                            + knownType.getName());
                }
                try {
                    value = convert(value, knownType);
                }
                catch (Exception e) {
                    throw new CompileException("cannot convert value of " + value.getClass().getName()
                            + " to: " + knownType.getName());
                }
            }

            //noinspection unchecked

            ((Receiver) variableMap.get(name)).receiver = value;
        }

        public Object getValue() {
            return ((Receiver) variableMap.get(name)).receiver;
        }

        public int getFlags() {
            return 0;
        }
    }


    public class InvokationContextFactory extends MapVariableResolverFactory {
        private VariableResolverFactory protoContext;

        public InvokationContextFactory(VariableResolverFactory next, VariableResolverFactory protoContext) {
            this.nextFactory = next;
            this.protoContext = protoContext;
        }

        @Override
        public VariableResolver createVariable(String name, Object value) {
            if (isResolveable(name) && !protoContext.isResolveable(name)) {
                return nextFactory.createVariable(name, value);
            }
            else {
                return protoContext.createVariable(name, value);
            }

        }

        @Override
        public VariableResolver createVariable(String name, Object value, Class<?> type) {
            if (isResolveable(name) && !protoContext.isResolveable(name)) {
                return nextFactory.createVariable(name, value, type);
            }
            else {
                return protoContext.createVariable(name, value, type);
            }
        }

        @Override
        public VariableResolver getVariableResolver(String name) {
            if (isResolveable(name) && !protoContext.isResolveable(name)) {
                return nextFactory.getVariableResolver(name);
            }
            else {
                return protoContext.getVariableResolver(name);
            }
        }

        @Override
        public boolean isTarget(String name) {
            return protoContext.isTarget(name);
        }

        @Override
        public boolean isResolveable(String name) {
            return protoContext.isResolveable(name) || nextFactory.isResolveable(name);
        }
    }
}