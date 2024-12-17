package com.apl;

import java.util.Objects;

public class Cue {
	private int offset;

	private String title;

	private int track;

	public Cue(String title, String offset) {
		super();
		this.title = title;
		try {
			this.offset = Integer.valueOf(offset);
		} catch (Exception e) {
			this.offset = (int) Math.ceil(Double.valueOf(offset));
		}
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		Cue other = (Cue) obj;
		return offset == other.offset && Objects.equals(title, other.title) && track == other.track;
	}

	public int getOffset() {
		return offset;
	}

	public String getTitle() {
		return title;
	}

	public int getTrack() {
		return track;
	}

	@Override
	public int hashCode() {
		return Objects.hash(offset, title, track);
	}

	public void setTrack(int track) {
		this.track = track;
	}

	@Override
	public String toString() {
		return "Cue [offset=" + offset + ", title=" + title + ", track=" + track + "]";
	}
}
