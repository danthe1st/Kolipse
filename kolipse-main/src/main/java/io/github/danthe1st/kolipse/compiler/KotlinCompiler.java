package io.github.danthe1st.kolipse.compiler;

import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;

import io.github.danthe1st.kolipse.nature.KotlinProjectNature;

public class KotlinCompiler {
	
	private final URLClassLoader compilerLoader;
	private MethodHandle cliToolExec;
	private MethodHandle k2JvmConstructor;
	
	public KotlinCompiler(Path compilerPath) throws Exception {
		Path libDir = compilerPath.resolve("lib");
		URL[] urls;
		try(Stream<Path> s = Files.list(libDir)){
			urls = s
				.filter(Files::isRegularFile)
				.filter(p -> p.toString().endsWith(".jar"))
				.map(p -> {
					try{
						return p.toUri().toURL();
					}catch(MalformedURLException e){
						throw new UncheckedIOException(e);
					}
				})
				.toArray(URL[]::new);
		}
		compilerLoader = new URLClassLoader(urls);
		Lookup lookup = MethodHandles.lookup();
		Class<?> k2JvmCompilerClass = compilerLoader.loadClass("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler");
		
		k2JvmConstructor = lookup.findConstructor(k2JvmCompilerClass, MethodType.methodType(void.class));
		Class<?> exitCodeClass = compilerLoader.loadClass("org.jetbrains.kotlin.cli.common.ExitCode");// this is an enum
		Class<?> cliToolClass = compilerLoader.loadClass("org.jetbrains.kotlin.cli.common.CLITool");
		
		cliToolExec = lookup.findVirtual(cliToolClass, "exec", MethodType.methodType(exitCodeClass, PrintStream.class, String[].class));
	}
	
	public String compile(PrintStream errorStream, IJavaProject project, List<String> sourceFiles, Path outputDirectory, List<String> extraCompilerArguments) throws Exception {
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
		return compile(errorStream, jvmPath, libs, javaSources, sourceFiles, outputDirectory, extraCompilerArguments);
	}
	
	private String compile(PrintStream errorStream, IPath jvmPath, List<IPath> libs, List<IPath> javaSourceDirectories, List<String> sourceFiles, Path outputDirectory, List<String> extraCompilerArguments) throws Exception {
		List<String> cmd = new ArrayList<>();
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
		return compile(cmd, errorStream);
	}
	
	private String compile(List<String> arguments, PrintStream errorStream) throws Exception {
		try{
			Object k2JvmCompiler = k2JvmConstructor.invoke();
			Enum<?> exitCode = (Enum<?>) cliToolExec.invoke(k2JvmCompiler, errorStream, arguments.toArray(String[]::new));
			return exitCode.name();
		}catch(Exception e){
			throw e;
		}catch(Throwable e){
			throw new Exception(e);
		}
	}
	
	private String createMultiplePathArgument(List<IPath> paths, String prefix) {
		StringBuilder argBuilder = new StringBuilder();
		argBuilder.append(prefix);
		for(Iterator<IPath> it = paths.iterator(); it.hasNext();){
			IPath path = it.next();
			argBuilder.append(path.toFile().getAbsolutePath());
			if(it.hasNext()){
				argBuilder.append(System.getProperty("os.name").toLowerCase().contains("win") ? ";" : ":");
			}
		}
		return argBuilder.toString();
	}
}
