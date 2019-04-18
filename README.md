# AkkaUI

Build your reactive UI using [Akka.js](https://github.com/akka-js/akka.js)

``` scala
import akka.ui._

implicit val system = ActorSystem("AkkaUI")
implicit val materializer = ActorMaterializer()

val textBox = input(placeholder := "Name").render
val name = span().render

val source: Source[Event, NotUsed] = textBox.source("input")
val sink: Sink[String, NotUsed] = name.sink("textContent")

source.map(_ => textBox.value).runWith(sink)

val root = div(
  textBox,
  h2("Hello ", name)
)

document.querySelector("#root").appendChild(root.render)
```

### Installation

``` scala
libraryDependencies += "net.pishen" %%% "akka-ui" % "0.5.0"
```

AkkaUI is built on top of [Scala.js](https://www.scala-js.org/), [Akka.js](https://github.com/akka-js/akka.js), and [scala-js-dom](https://github.com/scala-js/scala-js-dom).

### Setup Akka

Since AkkaUI uses Akka Streams underneath, we have to provide an Akka environment for it:

``` scala
implicit val system = ActorSystem("AkkaUI")
implicit val materializer = ActorMaterializer()
```

### Creating Sources

AkkaUI supports creating a `Source` from an `EventTarget`:

``` scala
import akka.ui._
import akka.stream.scaladsl.Source
import org.scalajs.dom.raw.HTMLButtonElement
import org.scalajs.dom.raw.MouseEvent
import scalatags.JsDom.all._

val btn: HTMLButtonElement = button("Click me!").render
val source: Source[MouseEvent, akka.NotUsed] = btn.source("click")
```

Here we use [Scalatags](https://github.com/lihaoyi/scalatags) to generate a `HTMLButtonElement` (which is also an `EventTarget`) in scala-js-dom. (Scalatags is not required. You can use whatever tool you want to build a DOM element.)

After getting the `HTMLButtonElement`, we can build a `Source` from it using `.source()`. (If you are not familiar with `Source`, you may check the [document](https://akka.io/docs/) of Akka Streams). The `.source()` function will expect an event type which is available on `HTMLButtonElement`. For example, `"click"`, `"focus"`, or `"mouseover"`. It will then check if this event has corresponding listener on the element using Scala Macros. For example, it will check if `onclick` is available on `HTMLButtonElement` when you give it `"click"`. This validation happens in compile time so you don't have to worry about misspelled event name:

```
[error] .../Main.scala:75:14: Couldn't find onclic listener on org.scalajs.dom.html.Button.
[error]       .source("clic")
[error]              ^
```

The `Source[MouseEvent, NotUsed]` returned by `.source()` can be materialized multiple times to achieve an event broadcasting effect. Also, it's OK to call `.source()` on the same element with same event type multiple times.

#### PreventDefault and StopPropagation
When calling `.source()`, it's possible to config the source using `preventDefault`, `stopPropagation`, or both:

``` scala
form.source("submit", preventDefault, stopPropagation)
```

But, for now, it will only accept these configurations on the first call to the `.source()` function. This may be fixed in the future version:

``` scala
val src1 = form.source("submit", preventDefault)

//the stopPropagation will not work here
val src2 = form.source("submit", stopPropagation)
```

### Creating Sinks

Similar to what you saw in previous section, AkkaUI also supports creating a `Sink` from `Element`:

``` scala
import akka.ui._
import akka.stream.scaladsl.Sink
import org.scalajs.dom.raw.HTMLSpanElement
import scalatags.JsDom.all._

val name: HTMLSpanElement = span().render
val sink: Sink[String, akka.NotUsed] = name.sink("textContent")
```

Given an `Element` instance, we can create a `Sink` on its...

* Properties

```scala
val sink: Sink[String, NotUsed] = span.sink("textContent")
val sink: Sink[Double, NotUsed] = span.sink("scrollTop")
...
```

* Styles

``` scala
val sink: Sink[String, NotUsed] = span.styleSink("backgroundColor")
...
```

* Children

``` scala
val sink: Sink[Seq[Element], NotUsed] = div.childrenSink
```

* Class

``` scala
val sink: Sink[Seq[String], NotUsed] = div.classSink
```

When creating the Sink from properties or styles, the String input will also be validated by Scala Macros to prevent misspelling. And the Sinks can be created or materialized any number of times.

Here is a simple example which mixes three kinds of Sinks:

``` scala
import akka.ui._
import org.scalajs.dom.ext._

def todoItem(content: String) = {
  val checkbox = input(`type` := "checkbox").render
  val status = span(cls := "font-weight-bold").render

  val clsSink = status.classSink.contramap[String] { stat =>
    if (stat == "TODO") {
      status.classList.filterNot(_ == "text-success") :+ "text-primary"
    } else {
      status.classList.filterNot(_ == "text-primary") :+ "text-success"
    }
  }
  val txtSink = status.sink("textContent")

  checkbox.source("change")
    .scan("TODO")((old, event) => if (old == "TODO") "DONE" else "TODO")
    .alsoTo(clsSink)
    .to(txtSink)
    .run()

  div(checkbox, " ", status, " ", content).render
}

val content = input().render
val btn = button("Add").render
val todoList = div().render

btn.source("click")
  .map(_ => todoList.children :+ todoItem(content.value))
  .runWith(todoList.childrenSink)

val root = div(content, btn, todoList)
document.querySelector("#root").appendChild(root.render)
```

Note that when we import `org.scalajs.dom.ext._` and `akka.ui._`, we will be able to operate `Element.children` and `Element.classList` like an immutable `Seq`, thanks to implicit classes.

If you want to keep some states in your stream, try using the `scan()` function from Akka Streams like above.

### Prevent Memory Leak

Each time you materialize a stream (with `run`, `runForeach`, or `runWith`), there will be several actors created underneath to handle the stream messages. These actors will not be terminated until the stream is completed from the `Source` or canceled from the `Sink`. Furthermore, if you materialize a stream using `Source.actorRef()` or `Sink.actorRef()`, the `Source` and `Sink` actors will keep listening for new message and will never complete. Hence, it's user's responsibility to terminate the streams by himself. (By sending a `PoisonPill` to the `Source` or `Sink` actors for example.)

In AkkaUI, when you create a `Source` or `Sink` from an `Element`, we will keep a binding information in the internal HashMap. When the `Element` is going to be removed from the DOM by `childrenSink`, all the `Source` and `Sink` related to this `Element` will be completed or canceled, hence prevent the stream from leaking. (These streams will not be terminated if you are removing the DOM element by yourself, so make sure you use `childrenSink` to do the modification.)

### Dynamic Stream Handling

In some use cases, we may have to reference back to a `Source` that's not yet materialized, or merge more `Source` into an already materialized stream. In these cases, one can use Akka's `MergeHub` and `BroadcastHub` to achieve the task. Following is another Todo-list example which add a "Remove" button after each Todo item, and cycle the "Remove" signal back to list's source:

``` scala
val contentInput = input().render
val addBtn = button("Add").render
val todoList = div().render

val (removeSink, removeSource) = MergeHub.source[String]
  .map("remove" -> _)
  .alsoTo(todoList.dummySink) // prevent memory leak
  .toMat(BroadcastHub.sink[(String, String)])(Keep.both)
  .run()

def todoItem(content: String) = {
  val checkbox = input(`type` := "checkbox").render
  val contentSpan = span(content).render
  val removeBtn = button("Remove").render

  checkbox.source("change")
    .scan(false)((done, _) => !done)
    .runWith(
      contentSpan.styleSink("textDecoration").contramap(
        done => if (done) "line-through" else "none"
      )
    )

  // connect removeBtn to removeSink
  removeBtn.source("click").map(_ => content).runWith(removeSink)

  div(checkbox, " ", contentSpan, " ", removeBtn).render
}

addBtn.source("click")
  .map(_ => "add" -> contentInput.value)
  .merge(removeSource)
  .scan(Map.empty[String, HTMLDivElement]) {
    case (map, ("add", content)) =>
      map + (content -> todoItem(content))
    case (map, ("remove", content)) =>
      map - content
  }
  .map(_.values.toSeq)
  .runWith(todoList.childrenSink)

val root = div(contentInput, addBtn, todoList)
document.querySelector("#root").appendChild(root.render)
```

Starting from a source that will merge the "add" and "remove" signal, we eventually convert each signal into several Todo items, where each Todo item will contain a Remove button which sends its "remove" signal (click) back to the starting source. To achieve this, we use `MergeHub` and `BroadcastHub` to get the `Sink` that can consume signals from the Remove button(s) and the `Source` which can be connected to `todoList`'s `childrenSink`.

Notice that when we call `run()`, an unhandled stream will be materialized, which we have to terminate by ourselves to prevent memory leak. Here we use a trick that add one more `Sink` to the `Source` before materializing it, which is the `dummySink` on `todoList`, this `Sink` will consume and ignore all the signals sending to it, and will cancel the stream when its binded DOM element is removed. When the stream is canceled, the materialized `MergeHub` and `BroadcastHub` will be terminated as well, hence preventing the memory leak. We can use this technique to connect the unhandled materialized stream to a DOM element which has the same life-cycle as the stream.
