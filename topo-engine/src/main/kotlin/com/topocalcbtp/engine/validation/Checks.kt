package com.topocalcbtp.engine.validation

/** Shared input checks. Distances and coordinates are in metres; angles are in decimal degrees. */
internal fun requireFinite(name: String, value: Double) {
    require(value.isFinite()) { "$name must be a finite number" }
}

internal fun requirePositive(name: String, value: Double) {
    requireFinite(name, value)
    require(value > 0.0) { "$name must be greater than zero" }
}

internal fun requireNonNegative(name: String, value: Double) {
    requireFinite(name, value)
    require(value >= 0.0) { "$name must not be negative" }
}

internal const val DEFAULT_TOLERANCE_METRES: Double = 1e-9
