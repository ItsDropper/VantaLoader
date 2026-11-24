package dev.itsdropper.vantaloader.gradle;
import groovy.json.JsonSlurper;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import java.io.File;
import java.util.Map;
/** Validates the compile-time API artifact and schema-1 mod descriptor before compilation. */
public abstract class VerifyVantaModTask extends DefaultTask {
 @InputFile @PathSensitive(PathSensitivity.NONE) public abstract RegularFileProperty getApiJar();
 @InputFile @PathSensitive(PathSensitivity.RELATIVE) public abstract RegularFileProperty getDescriptor();
 @TaskAction public void verify(){
  File api=getApiJar().get().getAsFile();File metadata=getDescriptor().get().getAsFile();
  if(!api.isFile())throw new GradleException("VantaLoader API JAR not found: "+api+" (set vantaMod.apiJar)");
  if(!metadata.isFile())throw new GradleException("Missing native mod descriptor: "+metadata);
  Object parsed;
  try{parsed=new JsonSlurper().parse(metadata);}catch(Exception ex){throw new GradleException("Invalid vanta.mod.json: "+ex.getMessage(),ex);}
  if(!(parsed instanceof Map<?,?> data))throw new GradleException("vanta.mod.json root must be an object");
  if(!(data.get("schemaVersion") instanceof Number schema)||schema.intValue()!=1)throw new GradleException("schemaVersion must be 1");
  if(!(data.get("id") instanceof String id)||!id.matches("[a-z][a-z0-9_-]{0,63}"))throw new GradleException("id must match [a-z][a-z0-9_-]{0,63}");
  if(!(data.get("name") instanceof String name)||name.isBlank())throw new GradleException("name is required");
  if(!(data.get("version") instanceof String version)||version.isBlank())throw new GradleException("version is required");
  if(!(data.get("entrypoints") instanceof Map<?,?> entrypoints)||!(entrypoints.get("main") instanceof String main)||main.isBlank())throw new GradleException("entrypoints.main is required");
  getLogger().lifecycle("Verified Vanta mod '{}' against {}",id,api.getName());
 }
}
