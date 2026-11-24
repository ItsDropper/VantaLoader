package dev.itsdropper.vantaloader.gradle;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.Project;
import javax.inject.Inject;
/** User-configurable paths for a native VantaLoader mod project. */
public abstract class VantaModExtension {
 private final RegularFileProperty apiJar;
 private final RegularFileProperty descriptor;
 @Inject public VantaModExtension(ObjectFactory objects,Project project){
  apiJar=objects.fileProperty().convention(project.getLayout().getProjectDirectory().file("libs/VantaLoader-api.jar"));
  descriptor=objects.fileProperty().convention(project.getLayout().getProjectDirectory().file("src/main/resources/vanta.mod.json"));
 }
 public RegularFileProperty getApiJar(){return apiJar;}
 public RegularFileProperty getDescriptor(){return descriptor;}
}
