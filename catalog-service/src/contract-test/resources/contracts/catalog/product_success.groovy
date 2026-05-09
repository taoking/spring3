package contracts.catalog

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description 'Return a product by sku'
    request {
        method GET()
        urlPath('/api/catalog/products/SKU-1001') {
            queryParameters {
                parameter 'slow': 'false'
                parameter 'fail': 'false'
            }
        }
        headers {
            header 'Authorization': 'Basic dXNlcjp1c2VyMTIz'
        }
    }
    response {
        status OK()
        headers {
            contentType applicationJson()
        }
        body(
                id: 1,
                sku: 'SKU-1001',
                name: 'Spring Boot 3 Guide',
                price: 99.00,
                active: true,
                fallback: false
        )
        bodyMatchers {
            jsonPath '$.id', byType()
            jsonPath '$.sku', byEquality()
            jsonPath '$.name', byType()
            jsonPath '$.price', byRegex(number())
            jsonPath '$.active', byType()
            jsonPath '$.fallback', byType()
        }
    }
}
