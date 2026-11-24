package dev.itsdropper.vantaloader.api;
import java.util.Objects;
import java.util.function.Predicate;
/** A typed, validated key in a mod's persistent VMA configuration. */
public final class ConfigKey<T> {
 public enum Type { STRING, BOOLEAN, INTEGER, LONG, DOUBLE, ENUM }
 private final String id; private final Type type; private final Class<T> javaType; private final T defaultValue; private final Predicate<T> validator;
 private ConfigKey(String id,Type type,Class<T> javaType,T value,Predicate<T> validator){
  if(id==null||!id.matches("[a-zA-Z][a-zA-Z0-9_.-]{0,127}"))throw new IllegalArgumentException("Invalid config key: "+id);
  this.id=id;this.type=Objects.requireNonNull(type);this.javaType=Objects.requireNonNull(javaType);this.defaultValue=Objects.requireNonNull(value);this.validator=Objects.requireNonNull(validator);validate(value);
 }
 public static ConfigKey<String> string(String id,String value){return new ConfigKey<>(id,Type.STRING,String.class,value,v->true);}
 public static ConfigKey<Boolean> bool(String id,boolean value){return new ConfigKey<>(id,Type.BOOLEAN,Boolean.class,value,v->true);}
 public static ConfigKey<Integer> integer(String id,int value){return integer(id,value,Integer.MIN_VALUE,Integer.MAX_VALUE);}
 public static ConfigKey<Integer> integer(String id,int value,int min,int max){if(min>max)throw new IllegalArgumentException("min must be <= max");return new ConfigKey<>(id,Type.INTEGER,Integer.class,value,v->v>=min&&v<=max);}
 public static ConfigKey<Long> longValue(String id,long value){return new ConfigKey<>(id,Type.LONG,Long.class,value,v->true);}
 public static ConfigKey<Double> decimal(String id,double value){return decimal(id,value,-Double.MAX_VALUE,Double.MAX_VALUE);}
 public static ConfigKey<Double> decimal(String id,double value,double min,double max){if(!Double.isFinite(min)||!Double.isFinite(max)||min>max)throw new IllegalArgumentException("Finite bounds required");return new ConfigKey<>(id,Type.DOUBLE,Double.class,value,v->Double.isFinite(v)&&v>=min&&v<=max);}
 public static <E extends Enum<E>> ConfigKey<E> enumeration(String id,Class<E> type,E value){return new ConfigKey<>(id,Type.ENUM,type,value,v->true);}
 public String id(){return id;} public Type type(){return type;} public T defaultValue(){return defaultValue;} public Class<T> javaType(){return javaType;}
 public void validate(T value){if(!javaType.isInstance(value)||!validator.test(value))throw new IllegalArgumentException("Invalid value for config key '"+id+"': "+value);}
}
