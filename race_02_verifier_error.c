#include <threads.h>
#include <stdlib.h>
#include <assert.h>

extern void abort(void);

void reach_error() { assert(0); }


// void ldv_assert(int expression) { if (!expression) { ERROR: {reach_error();abort();}}; return; }

void __VERIFIER_error(void);
void __VERIFIER_error(void) {
    ERROR : { reach_error(); abort(); }
    return;
}

int a = 0;

void Function( void* ignore )
{
    a = 1;

}

int main( void )
{
    thrd_t id;
    thrd_create( &id , Function , NULL );

    // ldv_assert(a == 1);
    int b = a;
    __VERIFIER_error();

    thrd_join( id , NULL );
}