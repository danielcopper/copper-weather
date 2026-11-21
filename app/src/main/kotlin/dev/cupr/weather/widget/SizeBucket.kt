package dev.cupr.weather.widget

import android.util.SizeF
import kotlin.math.floor

/**
 * The size buckets of D11, read from a widget's size in dp (D35): flat, one row high, shows "now" with today's high,
 * low and rain below it and, from 4 cells wide, the days beside it; small, from 2 rows high, shows "now"; wide, from 4
 * cells wide and 2 high, adds the hours; tall, from 4 wide and 3 high, adds the days. A widget 3 cells wide or less and
 * 2 rows or more high stays small however high it is.
 */
enum class SizeBucket {
    FLAT,
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

        /** What a size may fall short of its cells' and still count as them (D35). */
        private const val SLACK = 2f

        /** The bucket of a widget [size] dp large, its cells counted in portrait, the way the phones are held (D35). */
        fun of(size: SizeF): SizeBucket {
            val rows = cells(size.height, CELL_HEIGHT)
            return when {
                rows < WIDE_ROWS -> FLAT
                !wide(size) -> SMALL
                rows < TALL_ROWS -> WIDE
                else -> TALL
            }
        }

        /**
         * Whether a widget [size] dp large is 4 cells wide or more, which adds the hours, or in the flat bucket the
         * days (D11).
         */
        fun wide(size: SizeF): Boolean = cells(size.width, CELL_WIDTH) >= WIDE_CELLS

        /** The cells [dp] spans: the most n whose n cells, less the margin and the [SLACK], fit into it. */
        private fun cells(
            dp: Float,
            cell: Float,
        ): Int = floor((dp + CELL_MARGIN + SLACK) / cell).toInt()

        /** The size of a widget [columns] × [rows] cells large in portrait, by the same guidance. */
        fun sizeOf(
            columns: Int,
            rows: Int,
        ): SizeF = SizeF(columns * CELL_WIDTH - CELL_MARGIN, rows * CELL_HEIGHT - CELL_MARGIN)
    }
}
