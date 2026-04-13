x:int = 1
s:str = "hi"
nums:[int] = None

def add(a:int, b:int) -> int:
    return a + b

def first(s:str) -> str:
    return s[0]

class A(object):
    val:int = 0

    def __init__(self:"A"):
        self.val = 5

    def get(self:"A") -> int:
        return self.val

class B(A):
    def get(self:"B") -> int:
        return self.val + 1

a:A = None
b:B = None
y:int = 0

nums = [1, 2]
nums[0] = 3

a = A()
b = B()

y = add(2, 3)
y = a.get()
y = b.get()

if y > 0:
    y = y + 1
else:
    y = y + 2
