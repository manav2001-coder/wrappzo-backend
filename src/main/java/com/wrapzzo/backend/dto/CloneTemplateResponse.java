package com.wrapzzo.backend.dto;

/** The new template, how many features it got, and how many of the original's were left out (not on for the company, or not held by the one cloning). */
public record CloneTemplateResponse(TbacTemplateResponse template, int copied, int leftOut) {
}
