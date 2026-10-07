package com.opensourcefinder.github;

/**
 * The GitHub API could not be reached or answered with an error. The message is safe to show to users.
 */
public class GitHubException extends RuntimeException {

	public GitHubException(String message, Throwable cause) {
		super(message, cause);
	}
}
