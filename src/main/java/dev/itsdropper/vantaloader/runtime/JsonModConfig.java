package dev.itsdropper.vantaloader.runtime;
import com.google.gson.*;
import dev.itsdropper.vantaloader.api.*;
import java.io.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.util.*;
/** Gson-backed per-mod config with strict typed reads and atomic replacement. */
final class JsonModConfig implements ModConfig {
 private final Path file; private JsonObject values;
 JsonModConfig(Path file)throws IOException{this.file=file.toAbsolutePath().normalize();reload();}
 @Override public synchronized <T>T get(ConfigKey<T> key){
  Objects.requireNonNull(key);JsonElement e=values.get(key.id());if(e==null||e.isJsonNull())return key.defaultValue();
  try{Object v=switch(key.type()){
   case STRING->e.getAsString();case BOOLEAN->e.getAsBoolean();case INTEGER->e.getAsInt();case LONG->e.getAsLong();case DOUBLE->e.getAsDouble();
   case ENUM->Enum.valueOf((Class)key.javaType(),e.getAsString());};
   T result=key.javaType().cast(v);key.validate(result);return result;
  }catch(RuntimeException ex){throw new IllegalStateException("Invalid stored value for config key '"+key.id()+"' in "+file,ex);}
 }
 @Override public synchronized <T>void set(ConfigKey<T> key,T value){
  Objects.requireNonNull(key);key.validate(value);
  JsonElement e=switch(key.type()){
   case STRING->new JsonPrimitive((String)value);case ENUM->new JsonPrimitive(((Enum<?>)value).name());
   case BOOLEAN->new JsonPrimitive((Boolean)value);case INTEGER->new JsonPrimitive((Integer)value);
   case LONG->new JsonPrimitive((Long)value);case DOUBLE->new JsonPrimitive((Double)value);};
  values.add(key.id(),e);
 }
 @Override public synchronized boolean contains(String key){return values.has(key);}
 @Override public synchronized void remove(String key){values.remove(Objects.requireNonNull(key));}
 @Override public synchronized Set<String> keys(){return Collections.unmodifiableSet(new TreeSet<>(values.keySet()));}
 @Override public synchronized void save()throws IOException{
  Files.createDirectories(file.getParent());Path tmp=Files.createTempFile(file.getParent(),file.getFileName().toString(),".tmp");
  try{Files.writeString(tmp,new GsonBuilder().setPrettyPrinting().create().toJson(values)+System.lineSeparator(),StandardCharsets.UTF_8);
   try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
   catch(AtomicMoveNotSupportedException ex){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
  }finally{Files.deleteIfExists(tmp);}
 }
 @Override public synchronized void reload()throws IOException{
  if(!Files.exists(file)){values=new JsonObject();return;}
  try(Reader r=Files.newBufferedReader(file,StandardCharsets.UTF_8)){JsonElement root=JsonParser.parseReader(r);if(!root.isJsonObject())throw new IOException("Config root must be a JSON object: "+file);values=root.getAsJsonObject();}
  catch(JsonParseException|IllegalStateException ex){throw new IOException("Cannot parse mod config "+file+": "+ex.getMessage(),ex);}
 }
}
