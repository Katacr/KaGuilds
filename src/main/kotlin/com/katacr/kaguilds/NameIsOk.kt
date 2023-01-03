package com.katacr.kaguilds


fun checkName( name:String): Boolean {
    var x = 0
    var l = 0
    for (i in name){
        println(name[x])
        x += 1
        l += if (isAlp(name)){
            1
        } else
            2
    }
    return l > 12

}

fun isAlp(string: String): Boolean {
    val regex = """[a-zA-Z0-9_]+""".toRegex()
    return regex.containsMatchIn(input = string)


}

