macro_rules! declare_kotlin_object {
    (
        $(#[doc = $doc:literal])*
        $type_name:ident
    ) => {
        $(#[doc = $doc])*
        pub struct $type_name {
            kson_ref: KsonPtr,
        }

        impl FromKotlinObject for $type_name {
            fn from_kotlin_object(obj: kson_sys::kson_KNativePtr) -> Self {
                let kson_ref = KsonPtr {
                    inner: std::sync::Arc::new(OwnedKotlinPtr { inner: obj }),
                };

                Self { kson_ref }
            }
        }

        impl ToKotlinObject for $type_name {
            fn to_kotlin_object(&self) -> kson_sys::kson_KNativePtr {
                self.kson_ref.inner.inner
            }
        }
    };
}

macro_rules! impl_kotlin_object_for_enum {
    (
        $enum_type:ty,
        $variant1:path where $ty1:ty = $ty1_kotlin:expr,
        $variant2:path where $ty2:ty = $ty2_kotlin:expr,
    ) => {
        impl FromKotlinObject for $enum_type {
            fn from_kotlin_object(ptr: kson_sys::kson_KNativePtr) -> Self {
                #[allow(clippy::type_complexity)]
                static CONSTRUCTOR_MAP: std::sync::LazyLock<
                    Vec<(
                        util::KotlinType,
                        fn(kson_sys::kson_KNativePtr) -> $enum_type,
                    )>,
                > = std::sync::LazyLock::new(|| {
                    vec![
                        (
                            util::KotlinType {
                                inner: unsafe { $ty1_kotlin._type.unwrap()() },
                            },
                            |p| $variant1(<$ty1>::from_kotlin_object(p)),
                        ),
                        (
                            util::KotlinType {
                                inner: unsafe { $ty2_kotlin._type.unwrap()() },
                            },
                            |p| $variant2(<$ty2>::from_kotlin_object(p)),
                        ),
                    ]
                });

                let is_instance = KSON_SYMBOLS.IsInstance.unwrap();
                for (type_ptr, constructor_fn) in CONSTRUCTOR_MAP.iter() {
                    if unsafe { is_instance(ptr, type_ptr.inner) } {
                        return constructor_fn(ptr);
                    }
                }

                unreachable!()
            }
        }

        impl ToKotlinObject for $enum_type {
            fn to_kotlin_object(&self) -> kson_sys::kson_KNativePtr {
                match self {
                    $variant1(inner) => inner.to_kotlin_object(),
                    $variant2(inner) => inner.to_kotlin_object(),
                }
            }
        }
    };
}

macro_rules! impl_kotlin_object_for_c_enum {
    (
        $enum_type:ty,
        $(
            $ordinal:literal = $variant:path = $kotlin_ty:expr
        ),* $(,)?
    ) => {
        impl FromKotlinObject for $enum_type {
            fn from_kotlin_object(ptr: kson_sys::kson_KNativePtr) -> Self {
                match util::enum_ordinal(ptr) {
                    $(
                        $ordinal => $variant,
                    )*
                    _ => unreachable!(),
                }
            }
        }

        impl ToKotlinObject for $enum_type {
            fn to_kotlin_object(&self) -> kson_sys::kson_KNativePtr {
                match self {
                    $(
                        $variant => unsafe { $kotlin_ty.get.unwrap()() }.pinned,
                    )*
                }
            }
        }
    }
}
