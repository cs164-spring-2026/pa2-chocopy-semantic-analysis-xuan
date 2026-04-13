x:int = 0

def foo() -> int:
    y:int = 1

    def inner() -> int:
        y = 2          # no nonlocal
        return 0

    z = 3              # z not declared in scope
    return None        # wrong return


class A(object):
    val:int = 0

    def get(self:"A") -> int:
        return self.val


class B(A):
    def __init__(self:"B"):
        pass


b:B = None
b = B()

b.get_X()              # no method
b.get(1)               # wrong number of arguments