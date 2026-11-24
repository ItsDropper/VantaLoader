package dev.itsdropper.vantaloader.runtime;
import dev.itsdropper.vantaloader.api.ConfigKey;import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;import java.io.IOException;import java.nio.file.*;import static org.junit.jupiter.api.Assertions.*;
class JsonModConfigTest {
 @TempDir Path dir;
 @Test void persistsTypedValues()throws Exception{
  Path f=dir.resolve("config.json");var enabled=ConfigKey.bool("enabled",true);var count=ConfigKey.integer("count",3,0,10);var ratio=ConfigKey.decimal("ratio",.5,0,1);var mode=ConfigKey.enumeration("mode",Mode.class,Mode.AUTO);
  var c=new JsonModConfig(f);assertTrue(c.get(enabled));c.set(enabled,false);c.set(count,7);c.set(ratio,.75);c.set(mode,Mode.MANUAL);c.save();
  var loaded=new JsonModConfig(f);assertFalse(loaded.get(enabled));assertEquals(7,loaded.get(count));assertEquals(.75,loaded.get(ratio));assertEquals(Mode.MANUAL,loaded.get(mode));assertEquals(4,loaded.keys().size());
 }
 @Test void validatesWritesAndStoredValues()throws Exception{var key=ConfigKey.integer("volume",5,0,10);var c=new JsonModConfig(dir.resolve("bounded.json"));assertThrows(IllegalArgumentException.class,()->c.set(key,11));Path f=dir.resolve("wrong.json");Files.writeString(f,"{\"volume\":\"loud\"}");assertThrows(IllegalStateException.class,()->new JsonModConfig(f).get(key));}
 @Test void rejectsMalformedOrNonObjectFiles()throws Exception{Path f=dir.resolve("bad.json");Files.writeString(f,"[]");assertThrows(IOException.class,()->new JsonModConfig(f));Files.writeString(f,"{");assertThrows(IOException.class,()->new JsonModConfig(f));}
 @Test void reloadDiscardsUnsavedChangesAndPreservesUnknownKeys()throws Exception{Path f=dir.resolve("reload.json");Files.writeString(f,"{\"other\":\"preserved\"}");var c=new JsonModConfig(f);c.set(ConfigKey.string("local",""),"unsaved");c.reload();assertFalse(c.contains("local"));assertEquals("preserved",c.get(ConfigKey.string("other","")));}
 enum Mode{AUTO,MANUAL}
}
