package com.tivo.exoplayer.library.multiview;

import com.google.android.exoplayer2.ui.PlayerView;
import java.util.Objects;

/**
 * Abstracts the location of a {@link MultiViewPlayerController} in a "grid" of {@link PlayerView}'s.
 *
 * <p>The "grid" concept is loosing relavance, as now the single row view presents a linear list of
 * views.  For now, this property of the {@link MultiViewPlayerController} is immutable, however that looks
 * likely to change.</p>
 */
public class GridLocation {
  private final int row;
  private final int column;
  private final int viewIndex;

  public GridLocation(int row, int column, int viewIndex) {
    this.row = row;
    this.column = column;
    this.viewIndex = viewIndex;
  }

  /**
   * Location of the Grid Cell, 0..number of cells.  Independent of
   * if creation order is row or column major format.
   *
   * @return index of this Grid Cell
   */
  public int getViewIndex() {
    return viewIndex;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    GridLocation location = (GridLocation) o;
    return row == location.row && column == location.column && viewIndex == location.viewIndex;
  }

  @Override
  public int hashCode() {
    return Objects.hash(row, column, viewIndex);
  }

  @Override
  public String toString() {
    return "GridLocation{" +
        "row=" + row +
        ", column=" + column +
        '}';
  }
}
