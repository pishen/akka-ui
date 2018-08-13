# AkkaUI

Build your reactive UI using [Akka.js](https://github.com/akka-js/akka.js)

``` scala
import akka.ui._

implicit val system = ActorSystem("AkkaUI")
implicit val materializer = ActorMaterializer()

val textBox = input(placeholder := "Name").render
val name = span().render

val source: Source[Event, NotUsed] = textBox.source(_.oninput_=)
val sink: Sink[String, NotUsed] = name.sink(_.textContent_=)

source.map(_ => textBox.value).runWith(sink)

val root = div(
  textBox,
  h2("Hello ", name)
)

document.querySelector("#root").appendChild(root.render)
```

## Installation

``` scala
libraryDependencies += "net.pishen" %%% "akka-ui" % "0.1.0"
```

AkkaUI is built on top of [Scala.js](https://www.scala-js.org/), [Akka.js](https://github.com/akka-js/akka.js), and [scala-js-dom](https://github.com/scala-js/scala-js-dom).

## Usage

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
val source: Source[MouseEvent, akka.NotUsed] = btn.source {
  (elem: HTMLButtonElement) => elem.onclick_=
}
```

Here we use [Scalatags](https://github.com/lihaoyi/scalatags) to generate a `HTMLButtonElement` (which is also an `EventTarget`) in scala-js-dom. (Scalatags is not required. You can use whatever tool you want to build a DOM element.)

After getting the `HTMLButtonElement`, we can build a `Source` from it using `.source()`. (If you are not familiar with `Source`, you may check the [document](https://akka.io/docs/) of Akka Streams). The `.source()` function will expect you to return a listener setter to it. Since `onclick` is a `var` in `HTMLButtonElement`, we can use `onclick_=` to refer the [setter function](https://www.artima.com/pins1ed/stateful-objects.html#18.2).

Notice that you can only call `.source()` **once for each listener**. If you call `.source(_.onclick_=)` more than one time on the same element, the old listener will be overwritten by the new one and the old `Source` will not function properly. Instead, feel free to reuse (materialize) the same `Source` multiple times or pass it to other functions.

### Creating Sinks

Similar to what you saw in previous section, AkkaUI also supports creating a `Sink` from an `Element`:

``` scala
import akka.ui._
import akka.stream.scaladsl.Sink
import org.scalajs.dom.raw.HTMLSpanElement
import scalatags.JsDom.all._

val name: HTMLSpanElement = span().render
val sink: Sink[String, akka.NotUsed] = name.sink {
  (elem: HTMLSpanElement) => elem.textContent_=
}
```

Given an `Element` instance, we can create a `Sink` on its...

* Properties

```scala
val sink: Sink[String, NotUsed] = span.sink(_.textContent_=)
val sink: Sink[Double, NotUsed] = span.sink(_.scrollTop_=)
val sink: Sink[String, NotUsed] = span.sink(_.style.backgroundColor_=)
...
```

* Children

``` scala
val sink: Sink[Seq[Element], NotUsed] = div.childrenSink
```

* ClassList

``` scala
val sink: Sink[Seq[String], NotUsed] = div.classSink
```

Unlike the situation in previous section, you can create any number of `Sink` on the same property here. But it's recommended to also reuse the `Sink` here, since each call to `.sink()` will create an Actor and take up some space in your program.
