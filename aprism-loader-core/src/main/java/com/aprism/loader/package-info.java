/**
 * Core loader runtime for Aprism: the javaagent entry point
 * ({@link com.aprism.loader.AprismAgent}), the ASM-backed
 * {@link com.aprism.loader.AprismClassTransformer}, the knot-style
 * {@link com.aprism.loader.AprismClassLoader}, mod discovery
 * ({@link com.aprism.loader.ModDiscoverer}), and the singleton
 * {@link com.aprism.loader.AprismRuntime} that orchestrates the lifecycle.
 *
 * @author BlockConnect@StarsailsClover
 */
package com.aprism.loader;
