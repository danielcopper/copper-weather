package dev.cupr.weather.widget

import android.util.SizeF
import kotlin.math.floor

/**
 * The size buckets of D11, read from a widget's size in dp (D35): small shows "now"; wide, from 4 cells wide and 2
 * high, adds the hours; tall, from 4 wide and 3 high, adds the days. A widget 3 cells wide or less stays small however
 * high it is.
 */
enum class SizeBucket {
    SMALL,
    WIDE,
    TALL,
    ;

    companion object {
        private const val WIDE_CELLS = 4
        private const val WIDE_ROWS = 2
        private const val TALL_ROWS = 3

        /**
         * The width and height of a cell and the margin a widget loses, in dp, by Android's sizing guidance ("Determine
         * a size for your widget": n × m cells are (73n − 16) × (118m − 16) dp in portrait on a typical 5 × 4 phone).
         */
        private const val CELL_WIDTH = 73f
        private const val CELL_HEIGHT = 118f
        private const val CELL_MARGIN = 16f

        /** The bucket of a widget [size] dp large, its cells counted in portrait, the way the phones are held (D35). */
        fun of(size: SizeF): SizeBucket {
            val rows = cells(size.height, CELL_HEIGHT)
            return when {
                cells(size.width, CELL_WIDTH) < WIDE_CELLS || rows < WIDE_ROWS -> SMALL
                rows < TALL_ROWS -> WIDE
                else -> TALL
            }
        }

        /** The cells [dp] spans at least: the most n whose n cells, less the margin, fit into it. */
        private fun cells(
            dp: Float,
            cell: Float,
        ): Int = floor((dp + CELL_MARGIN) / cell).toInt()

        /** The size of a widget [columns] × [rows] cells large in portrait, by the same guidance. */
        fun sizeOf(
            columns: Int,
            rows: Int,
        ): SizeF = SizeF(columns * CELL_WIDTH - CELL_MARGIN, rows * CELL_HEIGHT - CELL_MARGIN)
    }
}
