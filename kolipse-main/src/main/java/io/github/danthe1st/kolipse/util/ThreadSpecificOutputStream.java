package io.github.danthe1st.kolipse.util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

public class ThreadSpecificOutputStream extends OutputStream {
	
	private static final ThreadSpecificOutputStream stdout;
	
	private ThreadLocal<OutputStream> threadLocal;
	private final OutputStream out;
	
	static{
		stdout = new ThreadSpecificOutputStream(System.out);
		System.setOut(new PrintStream(stdout));
	}
	
	public ThreadSpecificOutputStream(OutputStream out) {
		this.out = out;
	}
	
	public static ThreadSpecificOutputStream getStdout() {
		return stdout;
	}
	
	@Override
	public void write(byte[] b) throws IOException {
		getOutputStreamForCurrentThread().write(b);
	}
	
	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		getOutputStreamForCurrentThread().write(b, off, len);
	}
	
	@Override
	public void write(int b) throws IOException {
		getOutputStreamForCurrentThread().write(b);
	}
	
	private OutputStream getOutputStreamForCurrentThread() {
		OutputStream outputStream = threadLocal.get();
		if(outputStream == null){
			outputStream = out;
		}
		return outputStream;
	}
	
	public void unload() {
		threadLocal.remove();
	}
}
