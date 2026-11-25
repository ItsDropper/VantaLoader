package dev.itsdropper.vantaloader.api;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import static org.junit.jupiter.api.Assertions.*;
class VantaModApiTest {
 @Test void exposesIndependentSemanticApiVersion(){assertEquals("1.4.0",VantaModApi.VERSION);assertEquals(1,VantaModApi.VERSION_MAJOR);assertEquals(4,VantaModApi.VERSION_MINOR);}
 @Test void validatesRequiredCapabilitiesBeforeGameSpecificCode(){
  ModContext context=(ModContext)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ModContext.class},(proxy,method,args)->{
   if(method.getName().equals("minecraftCapabilities"))return MinecraftCapabilities.of(MinecraftCapability.CLIENT_LIFECYCLE_EVENTS,MinecraftCapability.OFFICIAL_MOJANG_MAPPINGS);
   if(method.getName().equals("toString"))return "test-context";
   return null;
  });
  assertDoesNotThrow(()->VantaModApi.requireCapabilities(context,MinecraftCapability.CLIENT_LIFECYCLE_EVENTS));
  UnsupportedOperationException failure=assertThrows(UnsupportedOperationException.class,()->VantaModApi.requireCapabilities(context,MinecraftCapability.SERVER_LIFECYCLE_EVENTS,MinecraftCapability.MIXIN_INTEROP));
  assertTrue(failure.getMessage().contains("SERVER_LIFECYCLE_EVENTS"));
  assertTrue(failure.getMessage().contains("MIXIN_INTEROP"));
 }
}
