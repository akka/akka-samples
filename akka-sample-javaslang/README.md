## Using Javaslang in Akka actors

The [Javaslang](http://www.javaslang.io/) library aims to reduce the amount of code and to increase the robustness of programs by applying functional programing ideas. This sample showcases how to write Akka programs by using immutable data structures and pattern matching features from Javaslang.

### Running the sample

This sample is an interactive application where you can spawn minions that crawl through the dungeon. You can also inspect the traveled path of every minion which stops its journey whenever it steps on a spot in the dungeon which it already visited.

To run the application, execute:

    mvn compile exec:java -Dexec.mainClass="sample.javaslang.MazeRunner"

You will be presented with a list of actions you can do in the sample:

    Available commands are:
      spawn N - spawn N number of minions
      results - show minion results
      heatmap - show minion journey heatmap
      q - quit

Try these out!

### Usage of Javaslang

#### Match

[Javaslang's Match expression](http://www.javaslang.io/javaslang-docs/#_pattern_matching) allows to pattern match on a given value and yield a result. This sample uses `Match` expression in quite a few places. Lets go through them.

> Matching on an incoming actor message in [Master.java](src/main/java/sample/javaslang/Master.java#L24) and performing a side effect

Here we use `Match` in the most simple form: match is applied on the incoming message and it tests whether the received message is of a certain type. Normally `Match` is used to transform one value to another. But here we want to perform a side effect (create an actor, change actor state, send message back to sender). Therefore we wrap all of the actions that need to be performed when a certain mesasge arrives to a `run` block.

> Matching on an incoming actor message in [Minion.java](src/main/java/sample/javaslang/Minion.java#L32) and yielding a next message

Here we use `Match` as an expression to yield a next message. Therefore no `run` blocks here. The full power of Javaslang's Match expression is used to match on a structure of the incoming message. The `Move` message provides a static method with an `@Unapply` annotation. The return of this method is then used by Javaslang to enable matching on the structure of `Move`.

For example the following will match on such instance of `Move` which attribute `to` is set to `None`:

     Case(Move(None()), ...)

It is also possible to match on a `Move` instance which has something set to its `to` attribute:

    Case(Move(Some($())), ...)

The following case only matches if `visited.contains(to)` returns `true`:

    Case(Move(Some($(visited::contains))), ...)

Such `Match` statements where instead of writing the code that inspect the structure of data, we can provide the expected structure makes programs concise and less prone to bugs.

#### Immutable collections

Another widely used feature from Javaslang in this sample is immutable collections. Actions on such collections (add, remove, update, ...) result in a new collection. This property enables a new style of writing programs by chaining operations on collections and providing various small functions to these operations.

Take a look at [MazeRunner.java](src/main/java/sample/javaslang/MazeRunner.java#L91) where a chain of three functions is used to convert a list of individual minion's results to a combined heat map of all the moves took by all of the minions.
