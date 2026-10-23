package com.photography.backend.gallery.entity;

/**
 * PORTFOLIO categories organise public gallery photos.
 * PACKAGE categories organise photos used as pricing-package cover images —
 * photos whose categories are all PACKAGE-type never appear in the public
 * gallery.
 * FEEDBACK is the reserved category for customer-feedback screenshots: those
 * photos are shown only in the feedback marquee and never in the gallery.
 */
public enum CategoryType { PORTFOLIO, PACKAGE, FEEDBACK }
