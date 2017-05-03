## Quick Overview

Congratulations! You have just created your first fault-resilient Akka application, nice job!

Let's start with an overview and discuss the problem we want to solve. This tutorial application demonstrates the use of Akka supervision hierarchies to implement reliable systems. This particular example demonstrates a calculator service that calculates arithmetic expressions. We will visit each of the components shortly, but you might want to take a quick look at the components before we move on.

- [Expression.java](src/main/java/supervision/Expression.java) contains our "domain model", a very simple representation of arithmetic expressions 
- [ArithmeticService.java](src/main/java/supervision/ArithmeticService.java) is the entry point for our calculation service 
- [FlakyExpressionCalculator.java](src/main/java/supervision/FlakyExpressionCalculator.java) is our heavy-lifter, a worker actor that can evaluate an expression concurrently 
- [Main.java](src/main/java/supervision/Main.java) example code that starts up the calculator service and sends a few jobs to it 

## The Expression Model

Our service deals with arithmetic expressions on integers involving addition, multiplication and (integer) division. In [Expression.java](src/main/java/supervision/Expression.java) you can see a very simple model of these kind of expressions.

Any arithmetic expression is a descendant of `Expression`, and have a left and right side (`Const` is the only exception) which is also an `Expression`.

For example, the expression (3 + 5) / (2 * (1 + 1)) could be constructed as:

    new Divide(
      new Add(
        new Const(3),
        new Const(5)
      ), // (3 + 5)
      new Multiply(
        new Const(2),
        new Add(
          new Const(1),
          new Const(1)
        ) // (1 + 1)
      ) // (2 * (1 + 1))
    ); // (3 + 5) / (2 * (1 + 1))

Apart from the encoding of an expression and some pretty printing, our model does not provide other services, so lets move on, and see how we can calculate the result of such expressions.

## Arithmetic Service

Our entry point is the [ArithmeticService](src/main/java/supervision/ArithmeticService.java) actor that accepts arithmetic expressions, calculates them and returns the result to the original sender of the `Expression`. This logic is implemented in the `receive` block. The actor handles `Expression` messages and starts a worker for them, carefully recording which worker belongs to which requester in the `pendingWorkers` map.

Who calculates the expression? As you see, on the reception of an `Expression` message we create a [FlakyExpressionCalculator](src/main/java/supervision/FlakyExpressionCalculator.java) actor and pass the expression as a parameter to its `Props`. What happens here is that we delegate the calculation work to a worker actor because the work can be "dangerous". After the worker finishes its job, it replies to its parent (in this case `ArithmeticService`) with a `Result` message. At this point the top level service actor looks up which actor it needs to send the final result to, and forwards it the value of the computation.

## The Dangers of Arithmetic

At first, it might feel strange that we don't calculate the result directly but we delegate it to a new actor. The reason for that, is that we want to treat the calculation as a dangerous task and isolate its execution in a different actor to keep the top level service safe.

In our example we will see two kinds of failures

- `FlakinessException` is a dummy exception that we throw randomly to simulate transient failures. We will assume that flakiness is temporary, and retrying the calculation is enough to eventually get rid of the failure. 
- Fatal failures, like `ArithmeticException` that will not go away no matter how many times we retry the task. Division by zero is a good example, since it indicates that the expression is invalid, and no amount of attempts to calculate it again will fix it. 

To handle these kind of failure modes differently we customized the supervisor strategy of ArithmeticService. Our strategy here is to restart the child when a recoverable error is detected (in our case the dummy `FlakinessException`), but when arithmetic errors happen — like division by zero — we have no hope to recover and therefore we stop the worker. In addition, we have to notify the original requester of the calculation job about the failure.

We used `OneForOneStrategy`, since we only want to act on the failing child, not on all of our children at the same time.

We set `loggingEnabled` to false, since we wanted to use our custom logging instead of the built-in reporting.

## The Joy of Calculation

We have now seen our `Expression` model, our fault modes and how we deal with them at the top level, delegating the dangerous work to child workers to isolate the failure, and setting `Stop` or `Restart` directives depending on the nature of the failure (fatal or transient). Now it's time to calculate and visit [FlakyExpressionCalculator.java](src/main/java/supervision/FlakyExpressionCalculator.java)!

Let's review first our evaluation strategy. When we are facing an expression like ((4 * 4) / (3 + 1)) we might be tempted to calculate (4 * 4) first, then (3 + 1), and then the final division. We can do better: Let's calculate the two sides of the division in parallel!

To achieve this, our worker delegates the calculation of the left and right side of the expression it has been given to two child workers of the same type (except in the case of constant, where it just sends its value as `Result` to its parent. This logic is in `preStart()` since this is the code that will be executed when an actor starts (and during restarts if the `postRestart()` is not overridden).

Since any of the sides of the original expression can finish before the other, we have to indicate somehow which side has been calculated, that is why we pass a `Position` as an argument to workers which they will put in their `Result` which they send after the calculation finished successfully.

## Failing Calculations

As you might have observed, we added a method called `flakiness()` that sometimes just misbehaves (throws a `FlakinessException`). This simulates a transient failure. Let's see how our FlakyExpressionCalculator deals with failure situations.

A supervisor strategy is applied to the children of an actor. Since our children are actually workers for calculating the left and right side of our subexpression, we have to think what different failures mean for us.

If we encounter a `FlakinessException` it indicates that one of our workers just made a hiccup and failed to calculate the answer. Since we know this failure is recoverable, we just restart the responsible worker.

In case of fatal failures we cannot really do anything ourselves. First of all, it indicates that the expression is invalid so restart does not help, second, we are not necessarily the top level worker for the expression. When an unknown failure is encountered it is escalated to the parent. The parent of this actor is either another `FlakyExpressionCalculator` or the `ArithmeticService `. Since the calculators all escalate, no matter how deep the failure happened, the `ArithmeticService` will decide on the fate of the job (in our case, stop it).

## When to Split Work? A Small Detour.

In our example we split expressions recursively and calculated the left and right sides of each of the expressions. The question naturally arises: do we gain anything here regarding performance?

In this example more probably not. There is an additional overhead of splitting up tasks and collecting results, and this case the actual subtasks consist of simple arithmetic operations which are very fast. To really gain in performance in practice, the actual subtasks have to be more heavyweight than this — but the pattern will be the same.

## Where to go from here?

After getting comfortable with the code, you can test your understanding by trying to solve the following small exercises:

- Add `flakiness()` to various places in the calculator and see what happens 
- Try devising more calculation intensive nested jobs instead of arithmetic expressions (for example transformations of a text document) where parallelism improves performance 

You should also visit

- [The Akka documentation](http://doc.akka.io/docs/akka/2.5/java.html)
- [Documentation of supervision](http://doc.akka.io/docs/akka/2.5/java/fault-tolerance.html)
- [The Akka Team blog](http://blog.akka.io)
