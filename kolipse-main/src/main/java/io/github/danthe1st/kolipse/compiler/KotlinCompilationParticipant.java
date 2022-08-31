package io.github.danthe1st.kolipse.compiler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.CompilationParticipant;
import org.eclipse.jdt.core.compiler.IProblem;

import io.github.danthe1st.kolipse.nature.KotlinProjectNature;

public class KotlinCompilationParticipant extends CompilationParticipant {
	// TODO incremental compilation?
	
	private static final String MARKER_ATTRIBUTE = "kolipse_marker";
	private List<IMarker> markers = new ArrayList<>();
	
	private void removeMarkers() throws CoreException {
		for(Iterator<IMarker> it = markers.iterator(); it.hasNext();){
			IMarker marker = it.next();
			marker.delete();
			it.remove();
		}
	}
	
	boolean firstBuild = true;
	
	@Override
	public int aboutToBuild(IJavaProject project) {
		try{
			KotlinProjectNature kotlinNature = (KotlinProjectNature) project.getProject().getNature(KotlinProjectNature.NATURE_ID);
			if(firstBuild){
				firstBuild = false;
				for(IMarker marker : project.getProject().findMarkers(null, true, 0)){
					if(marker.getAttribute(MARKER_ATTRIBUTE, false)){
						markers.add(marker);
					}
				}
			}else{
				removeMarkers();
			}
			IClasspathEntry[] resolvedClasspath;
			try{
				resolvedClasspath = project.getResolvedClasspath(true);
				List<String> kotlinSources = new ArrayList<>();
				for(IClasspathEntry classpathEntry : resolvedClasspath){
					if(classpathEntry.getEntryKind() == IClasspathEntry.CPE_SOURCE){
						try(Stream<Path> s = Files.walk(project.getProject().getFile(classpathEntry.getPath().lastSegment()).getLocation().toFile().toPath())){
							kotlinSources.addAll(
									s
										.filter(Files::isRegularFile)
										.filter(p -> p.toString().endsWith(".kt"))
										.map(p -> p.toAbsolutePath().toString())
										.collect(Collectors.toList())
							);
						}
					}
				}
				if(!kotlinSources.isEmpty()){
					try(Stream<Path> s = Files.walk(kotlinNature.getKotlinOutputFullPath())){
						s
							.skip(1)
							.sorted(Comparator.reverseOrder())
							.forEach(t -> {
								try{
									Files.delete(t);
								}catch(IOException e){
									throw new UncheckedIOException("Cannot delete Kotlin output directory", e);
								}
							});
					}
					Process p = compile(project, kotlinSources, kotlinNature.getKotlinOutputFullPath(), Collections.emptyList());
					try{
						p.waitFor();
						if(p.exitValue() != 0){
							IMarker marker = project.getProject().createMarker("org.eclipse.jdt.core.problem");
							markers.add(marker);
							marker.setAttribute(MARKER_ATTRIBUTE, true);
							marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
							try(BufferedReader br = new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))){
								marker.setAttribute(IMarker.MESSAGE, "Kolipse: Compiling Kotlin code failed. See the full error description for details.\n" + br.lines().collect(Collectors.joining("\n")));
							}
							marker.setAttribute(IJavaModelMarker.ID, IProblem.ExternalProblemNotFixable);
						}
					}catch(CoreException e){
						throw new RuntimeException(e);
					}catch(InterruptedException e1){
						Thread.currentThread().interrupt();
						p.destroy();
					}
					project.getProject().getFile(kotlinNature.getKotlinOutputPath()).refreshLocal(IResource.DEPTH_ZERO, null);
					project.getProject().refreshLocal(IResource.DEPTH_INFINITE, null);
				}
			}catch(JavaModelException e){
				throw new RuntimeException(e);
			}catch(IOException e){
				throw new RuntimeException(e);
			}
			return super.aboutToBuild(project);
		}catch(CoreException e){
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public void cleanStarting(IJavaProject project) {
		super.cleanStarting(project);
		try{
			KotlinProjectNature nature = (KotlinProjectNature) project.getProject().getNature(KotlinProjectNature.NATURE_ID);
			try(Stream<Path> s = Files.walk(nature.getKotlinOutputFullPath())){
				s
					.skip(1)
					.sorted(Comparator.reverseOrder())
					.forEach(t -> {
						try{
							Files.delete(t);
						}catch(IOException e){
							throw new RuntimeException(e);
						}
					});
			}
		}catch(CoreException | IOException e){
			throw new RuntimeException(e);
		}
		
	}
	
	@Override
	public boolean isActive(IJavaProject project) {
		try{
			return project.getProject().isNatureEnabled(KotlinProjectNature.NATURE_ID) && ((KotlinProjectNature) project.getProject().getNature(KotlinProjectNature.NATURE_ID)).getKotlinCompilerPath() != null;
		}catch(CoreException e){
			throw new RuntimeException(e);
		}
	}
	
	public static Process compile(IJavaProject project, List<String> sourceFiles, Path outputDirectory, List<String> extraCompilerArguments) throws IOException, CoreException {
		KotlinProjectNature kotlinNature = (KotlinProjectNature) project.getProject().getNature(KotlinProjectNature.NATURE_ID);
		extraCompilerArguments = new ArrayList<>();
		String javaPlatformLevel = project.getOption("org.eclipse.jdt.core.compiler.codegen.targetPlatform", true);
		if(javaPlatformLevel != null && !javaPlatformLevel.isEmpty()){
			extraCompilerArguments.add("-jvm-target");
			extraCompilerArguments.add(javaPlatformLevel);
		}
		List<IPath> libs = new ArrayList<>();
		List<IPath> javaSources = new ArrayList<>();
		IPath jvmPath = null;
		IClasspathEntry[] resolvedClasspath = project.getResolvedClasspath(true);
		IClasspathEntry[] rawClasspath = project.getRawClasspath();
		for(int i = 0; i < resolvedClasspath.length; i++){
			IClasspathEntry classpathEntry = resolvedClasspath[i];
			classpathEntry.getEntryKind();
			switch(classpathEntry.getEntryKind()) {
				case IClasspathEntry.CPE_LIBRARY:
					if(!classpathEntry.getPath().equals(kotlinNature.getKotlinOutputPath())){
						if(rawClasspath[i].getPath().toString().contains("org.eclipse.jdt.launching.JRE_CONTAINER")){
							jvmPath = classpathEntry.getPath();
						}else{
							libs.add(classpathEntry.getPath());
						}
					}
					break;
				case IClasspathEntry.CPE_SOURCE:
					javaSources.add(project.getProject().getFile(classpathEntry.getPath().lastSegment()).getLocation());
					break;
				case IClasspathEntry.CPE_CONTAINER:
				
				default:
					break;
			}
		}
		return compile(kotlinNature.getKotlinCompilerPath(), jvmPath, libs, javaSources, sourceFiles, outputDirectory, extraCompilerArguments);
	}
	
	private static Process compile(Path kotlinCompilerPath, IPath jvmPath, List<IPath> libs, List<IPath> javaSourceDirectories, List<String> sourceFiles, Path outputDirectory, List<String> extraCompilerArguments) throws IOException {
		List<String> cmd = new ArrayList<>();
		cmd.add(kotlinCompilerPath.toString());
		if(jvmPath != null){
			cmd.add("-jdk-home");
			cmd.add(jvmPath.toFile().getParentFile().getParentFile().getAbsolutePath());
		}
		cmd.add("-classpath");
		cmd.add(createMultiplePathArgument(libs, ""));
		cmd.add(createMultiplePathArgument(javaSourceDirectories, "-Xjava-source-roots="));
		cmd.add("-d");
		cmd.add(outputDirectory.toString());
		for(String arg : extraCompilerArguments){
			cmd.add(arg);
		}
		for(String sourceFile : sourceFiles){
			cmd.add(sourceFile);
		}
		return new ProcessBuilder(cmd)
			.redirectOutput(Redirect.INHERIT)
			.redirectError(Redirect.PIPE)
			.start();
	}
	
	private static String createMultiplePathArgument(List<IPath> paths, String prefix) {
		StringBuilder argBuilder = new StringBuilder();
		if(System.getProperty("os.name").toLowerCase().contains("win")){
			argBuilder.append('"');
		}
		argBuilder.append(prefix);
		for(Iterator<IPath> it = paths.iterator(); it.hasNext();){
			IPath path = it.next();
			argBuilder.append(path.toFile().getAbsolutePath());
			if(it.hasNext()){
				argBuilder.append(System.getProperty("os.name").toLowerCase().contains("win") ? ";" : ":");
			}
		}
		if(System.getProperty("os.name").toLowerCase().contains("win")){
			argBuilder.append('"');
		}
		return argBuilder.toString();
	}
}
