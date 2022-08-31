package io.github.danthe1st.kolipse.nature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.preferences.IScopeContext;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

public class KotlinProjectNature implements IProjectNature {
	
	private static final Bundle BUNDLE = FrameworkUtil.getBundle(KotlinProjectNature.class);
	public static final String NATURE_ID = "io.github.danthe1st.kolipse.KotlinNature";
	public static final String KOLIPSE_MARKER_ID = "io.github.danthe1st.kolipse.kotlincompilationerror";
	
	private IProject project;
	
	private IPath kotlinOutputPath;
	
	private Path kotlinOutputFullPath;
	private Preferences prefs;
	
	@Override
	public void configure() throws CoreException {
		if(project.isNatureEnabled("org.eclipse.jdt.core.javanature")){
			IJavaProject javaProject = JavaCore.create(project);
			if(!Files.exists(kotlinOutputFullPath)){
				try{
					Files.createDirectory(kotlinOutputFullPath);
					project.refreshLocal(IResource.DEPTH_ONE, null);
				}catch(IOException e){
					throw new CoreException(new Status(IStatus.ERROR, getClass(), "Cannot create Kotlin output directory"));
				}
			}
			boolean addKotlinStdlib = javaProject.findType("kotlin.Unit") == null;
			IClasspathEntry kotlinOutputEntry = JavaCore.newLibraryEntry(kotlinOutputPath, null, null);
			List<IClasspathEntry> classpath = new ArrayList<>(Arrays.asList(javaProject.getRawClasspath()));
			classpath.add(kotlinOutputEntry);
			if(addKotlinStdlib){
				classpath.add(createClasspathEntryFromLibJarInKotlinCompiler("kotlin-stdlib"));
				classpath.add(createClasspathEntryFromLibJarInKotlinCompiler("kotlin-stdlib-jdk8"));
			}
			javaProject.setRawClasspath(classpath.toArray(IClasspathEntry[]::new), null);
		}else{
			throw new CoreException(new Status(IStatus.CANCEL, getClass(), "Cannot add Kotlin nature to non-Java projects"));
		}
	}
	
	private IClasspathEntry createClasspathEntryFromLibJarInKotlinCompiler(String libJarName) {
		return JavaCore.newLibraryEntry(resolveLibJarInKotlinCompiler(libJarName + ".jar"), resolveLibJarInKotlinCompiler(libJarName + "-sources.jar"), null);
	}
	
	private IPath resolveLibJarInKotlinCompiler(String libJarName) {
		return org.eclipse.core.runtime.Path.fromOSString(getKotlinCompilerPath().getParent().getParent().resolve("lib/" + libJarName).toString());
	}
	
	@Override
	public void deconfigure() throws CoreException {
		if(project.isNatureEnabled("org.eclipse.jdt.core.javanature")){
			IJavaProject javaProject = JavaCore.create(project);
			IClasspathEntry[] oldClasspath = javaProject.getRawClasspath();
			IClasspathEntry[] newClasspath = Arrays.stream(oldClasspath)
				.filter(entry -> !entry.getPath().equals(kotlinOutputPath))
				.toArray(IClasspathEntry[]::new);
			javaProject.setRawClasspath(newClasspath, null);
			try(Stream<Path> s = Files.walk(kotlinOutputFullPath)){
				s.sorted(Comparator.reverseOrder()).forEach(t -> {
					try{
						Files.delete(t);
					}catch(IOException e){
						throw new UncheckedIOException(e);
					}
				});
			}catch(IOException e){
				e.printStackTrace();
			}
		}
	}
	
	@Override
	public IProject getProject() {
		return project;
	}
	
	@Override
	public void setProject(IProject project) {
		this.project = project;
		prefs = getPreferencesForProject(project);
		
		String outputDirPortableString = prefs.get("output_dir", null);
		if(outputDirPortableString == null){
			IFile kotlinOutputFile = project.getFile(".kotlin-output");
			kotlinOutputPath = kotlinOutputFile.getFullPath();
			kotlinOutputFullPath = kotlinOutputFile.getLocation().toFile().toPath();
			prefs.put("output_dir", kotlinOutputPath.toPortableString());
			try{
				prefs.flush();
			}catch(BackingStoreException e){
				throw new RuntimeException(e);
			}
		}else{
			kotlinOutputPath = org.eclipse.core.runtime.Path.fromPortableString(outputDirPortableString);
			kotlinOutputFullPath = project.getWorkspace().getRoot().getFile(kotlinOutputPath).getLocation().toFile().toPath();
		}
		
	}
	
	public IPath getKotlinOutputPath() {
		return kotlinOutputPath;
	}
	
	public Path getKotlinOutputFullPath() {
		return kotlinOutputFullPath;
	}
	
	private static Preferences getPreferencesForProject(IProject project) {
		IScopeContext ctx = new ProjectScope(project);
		return ctx.getNode(NATURE_ID);
	}
	
	public void setKotlinCompilerPath(Path kotlinCompilerPath) throws BackingStoreException {
		prefs.put("compiler-path", kotlinCompilerPath.toString());
		prefs.flush();
	}
	
	public Path getKotlinCompilerPath() {
		String path = prefs.get("compiler-path", null);
		Path compilerPath;
		if(path == null){
			compilerPath = BUNDLE.getDataFile("kotlin-compiler").toPath();
		}else{
			compilerPath = Path.of(path);
		}
		if(Files.exists(compilerPath)){
			return compilerPath;
		}else{
			return null;
		}
	}
}
