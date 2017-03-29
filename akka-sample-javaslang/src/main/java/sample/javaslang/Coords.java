package sample.javaslang;

import static javaslang.API.Case;
import static javaslang.API.Match;

final class Coords {
  final public Integer x;
  final public Integer y;

  public Coords(Integer x, Integer y) {
    this.x = x;
    this.y = y;
  }

  public Coords translate(Maze.Translation t) {
    return Match(t).of(
      Case(Maze.Translation.TOP, new Coords(this.x, this.y - 1)),
      Case(Maze.Translation.DOWN, new Coords(this.x, this.y + 1)),
      Case(Maze.Translation.LEFT, new Coords(this.x - 1, this.y)),
      Case(Maze.Translation.RIGHT, new Coords(this.x + 1, this.y))
    );
  }

  public String toString() {
    return "Coords[" + x + "," + y + "]";
  }

  public int hashCode() {
    return (41 * (41 + x) + y);
  }

  public boolean equals(Object other) {
    boolean result = false;
    if (other instanceof Coords) {
      Coords that = (Coords)other;
      result = (this.x == that.x && this.y == that.y);
    }
    return result;
  }
}
