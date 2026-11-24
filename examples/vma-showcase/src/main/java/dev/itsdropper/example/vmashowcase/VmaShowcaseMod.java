package dev.itsdropper.example.vmashowcase;
import dev.itsdropper.vantaloader.api.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicBoolean;
/** Demonstrates ordinary mod behavior using VMA without bytecode transformations. */
public final class VmaShowcaseMod implements VantaMod {
 private static final ConfigKey<Boolean> ENABLED=ConfigKey.bool("enabled",true);
 private final AtomicBoolean observed=new AtomicBoolean();
 public VmaShowcaseMod(){}
 @Override public void onInitialize(ModContext context){
  ModConfig config=context.config();
  if(!config.contains(ENABLED.id())){config.set(ENABLED,true);try{config.save();}catch(IOException e){throw new UncheckedIOException("Cannot create VMA showcase config",e);}}
  boolean enabled=config.get(ENABLED);
  context.logger().info("VMA showcase config loaded: enabled="+enabled);
  if(!enabled)return;
  context.tasks().execute(()->context.logger().info("VMA showcase: managed background task executed"));
  context.events().subscribe(ClientTickEvent.class,0,event->{
   if(event.phase()==ClientTickEvent.Phase.START&&observed.compareAndSet(false,true))
    context.logger().info("VMA showcase: high-level client tick event delivered");
  });
 }
 @Override public void onShutdown(){}
}
