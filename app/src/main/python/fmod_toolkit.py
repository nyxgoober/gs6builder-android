"""
This is a stub.
"""


def __getattr__(name):
    if name.startswith("__"):
        raise AttributeError(name)

    def _unavailable(*args, **kwargs):
        raise RuntimeError(f"fmod_toolkit.{name} is not available on Android")

    return _unavailable