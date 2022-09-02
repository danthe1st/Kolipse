package io.github.danthe1st.kolipse;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.BundleContext;

import io.github.danthe1st.kolipse.compiler.KotlinCompiler;

public class KolipsePlugin extends Plugin {
	private static KolipsePlugin instance;
	
	private Map<Path, KotlinCompiler> compilers = new ConcurrentHashMap<>();
	
	public static KolipsePlugin getInstance() {
		synchronized(KolipsePlugin.class){
			if(instance == null){
				throw new IllegalStateException("Plugin not yet initialized");
			}
			return instance;
		}
	}
	
	public KolipsePlugin() {
		synchronized(KolipsePlugin.class){
			if(instance != null){
				throw new IllegalStateException("Cannot instantiate plugin class multiple times");
			}
			instance = this;
		}
	}
	
	@Override
	public synchronized void stop(BundleContext context) throws Exception {
		super.stop(context);
		Exception toThrow = null;
		for(AutoCloseable closeable : compilers.values()){
			try{
				closeable.close();
			}catch(IOException e){
				if(toThrow == null){
					toThrow = e;
				}else{
					toThrow.addSuppressed(e);
				}
			}
		}
		if(toThrow != null){
			throw toThrow;
		}
	}
	
	public KotlinCompiler getKotlinCompiler(Path compilerPath) throws Exception {
		try{
			return compilers.computeIfAbsent(compilerPath, p -> {
				try{
					KotlinCompiler compiler = new KotlinCompiler(compilerPath);
					return compiler;
				}catch(ClassNotFoundException | NoSuchMethodException | IllegalAccessException | IOException e){
					throw new WrapperException(e);
				}
			});
		}catch(WrapperException e){
			throw e.getCause();
		}
	}
	
	private static class WrapperException extends RuntimeException {
		public WrapperException(Exception e) {
			super(e);
		}
		
		@Override
		public Exception getCause() {
			return (Exception) super.getCause();
		}
	}
}
