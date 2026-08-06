/**
 * Manifest parsing, validation, and dependency resolution for the Aprism loader.
 * This module reads {@code aprism.manifest.json} files (and converts legacy
 * Fabric, NeoForge, Forge, and LiteLoader manifests) into
 * {@link com.aprism.manifest.AprismManifest} records, validates them against
 * the schema, and resolves the load order via
 * {@link com.aprism.manifest.DependencyResolver}.
 *
 * @author BlockConnect@StarsailsClover
 */
package com.aprism.manifest;
