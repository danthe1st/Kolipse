package io.github.danthe1st.kolipse.quickfix;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class CountingInputStream extends FilterInputStream {
	
	private long counter;
	
	public CountingInputStream(InputStream in) {
		super(in);
	}
	
	@Override
	public int read() throws IOException {
		int ret = super.read();
		if(ret != -1){
			counter++;
		}
		return ret;
	}
	
	@Override
	public int read(byte[] b) throws IOException {
		int ret = super.read(b);
		if(ret != -1){
			counter += ret;
		}
		return ret;
	}
	
	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int ret = super.read(b, off, len);
		if(ret != -1){
			counter += ret;
		}
		return ret;
	}
	
	public long getCounter() {
		return counter;
	}
}
