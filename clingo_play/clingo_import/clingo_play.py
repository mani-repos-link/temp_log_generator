import clingo


class ExampleApp:

    @staticmethod
    def divisors(a):
        a = a.number
        for i in range(1, a+1):
            if a % i == 0:
                yield clingo.Number(i)

    # @staticmethod
    def my_print(self, a):
        print("match", a)

    def run (self):
        ctl = clingo.Control()
        ctl.load("example.lp")
        ctl.ground([("base", [])], context=self)
        ctl.solve(on_model=self.my_print)


if __name__ == "__main__":
    ExampleApp().run()

