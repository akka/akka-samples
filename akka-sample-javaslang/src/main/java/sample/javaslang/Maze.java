package sample.javaslang;

import javaslang.collection.List;
import javaslang.control.Option;
import javaslang.match.annotation.Patterns;

import java.util.Arrays;
import java.util.Collections;

import static javaslang.API.*;

@Patterns
public class Maze {

  public Maze(List<String> layout) {

  }

  public boolean isLegal(Coords c) {
    return true;
  }

  public boolean isFinish(Coords c) {
    return true;
  }

  public Option<Coords> legalFrom(Coords coords) {
    return Translation.randomOrder()
      .map(coords::translate)
      .filter(this::isLegal)
      .toOption();
  }

  enum Translation {
    TOP, RIGHT, DOWN, LEFT;

    public static List<Translation> randomOrder() {
      java.util.List<Translation> translations = Arrays.asList(values());
      Collections.shuffle(translations);
      return List.ofAll(translations);
    }

    public static Translation getRandom() {
      return values()[(int) (Math.random() * values().length)];
    }
  }
}
