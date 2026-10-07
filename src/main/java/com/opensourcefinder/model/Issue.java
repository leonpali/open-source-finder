package com.opensourcefinder.model;

import java.util.List;

public record Issue(int number, String title, String body, String url, List<String> labels, int comments) {
}
