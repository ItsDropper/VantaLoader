package dev.itsdropper.vantaloader.gradle;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.TaskProvider;
/** Native VantaLoader Gradle integration. */
public final class VantaModPlugin implements Plugin<Project> {
 @Override public void apply(Project project){
  project.getPluginManager().apply(JavaPlugin.class);
  VantaModExtension extension=project.getExtensions().create("vantaMod",VantaModExtension.class,project.getObjects(),project);
  project.getDependencies().add("compileOnly",project.files(extension.getApiJar()));
  TaskProvider<VerifyVantaModTask> verify=project.getTasks().register("verifyVantaMod",VerifyVantaModTask.class,task->{
   task.setGroup("verification");task.setDescription("Validates the VantaLoader API JAR and native mod descriptor.");
   task.getApiJar().set(extension.getApiJar());task.getDescriptor().set(extension.getDescriptor());
  });
  project.getTasks().named(JavaPlugin.COMPILE_JAVA_TASK_NAME).configure(task->task.dependsOn(verify));
 }
}
