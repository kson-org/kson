mod generated;
#[cfg(test)]
mod test;

pub use generated::*;

fn kson_result_into_rust_result(r: Result) -> std::result::Result<result::Success, result::Failure> {
    match r {
        Result::Success(s) => Ok(s),
        Result::Failure(f) => Err(f),
    }
}

fn kson_schema_result_into_rust_result(r: SchemaResult) -> std::result::Result<schema_result::Success, schema_result::Failure> {
    match r {
        SchemaResult::Success(s) => Ok(s),
        SchemaResult::Failure(f) => Err(f),
    }
}