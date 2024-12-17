package com.apl;

import java.util.Map;
import java.util.TreeMap;

public class Request {
	private String fileName;
	private boolean fromCache = false;
	Map<String, String> headers = new TreeMap<>();
	private String url;

	public void addHeader(String k, String v) {
		headers.put(k, v);
	}

	@Override
	public boolean equals(Object o) {
		// If the object is compared with itself then return true
		if (o == this) {
			return true;
		}

		/*
		 * Check if o is an instance of Complex or not "null instanceof [type]" also
		 * returns false
		 */
		if (!(o instanceof Request)) {
			return false;
		}

		// typecast o to Complex so that we can compare data members
		Request r = (Request) o;

		// Compare the data members and return accordingly
		String getUrl = getUrl();

		if (getUrl != null && r != null) {
			return getUrl.equals(r.getUrl());
		}
		return false;
	}

	public String getFileName() {
		return fileName;
	}

	public Map<String, String> getHeaders() {
		return headers;
	}

	public String getUrl() {
		return url;
	}

	public boolean isFromCache() {
		return fromCache;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public void setFromCache(boolean fromCache) {
		this.fromCache = fromCache;
	}

	public void setUrl(String url) {
		this.url = url;
		if (url.contains("-Part") && url.contains("?")) {
			String name = url.substring(url.indexOf("-Part") + 1, url.indexOf("?"));
			setFileName(name);
		} else if (url.endsWith(".mp4")) {
			String name = url.substring(url.lastIndexOf("-") + 1);
			setFileName(name);
		}
	}

	@Override
	public String toString() {
		return url;
	}
}
